package com.ameli.pdfstitcher;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.FileDialog;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class SplitPanel extends JPanel {
    private final SplitGroupTableModel tableModel = new SplitGroupTableModel();
    private final JTable groupTable = new JTable(tableModel) {
        @Override
        public String getToolTipText(java.awt.event.MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            int column = columnAtPoint(event.getPoint());
            if (row < 0 || column < 0) {
                return null;
            }

            Object value = getValueAt(row, column);
            if (value == null) {
                return null;
            }

            String text = value.toString().trim();
            if (text.isEmpty()) {
                return null;
            }

            return "<html>" + text.replace("\n", "<br/>") + "</html>";
        }
    };
    private final JLabel sourceNameLabel = new JLabel("No source PDF selected");
    private final JLabel sourceMetaLabel = new JLabel("Choose a single PDF, then allocate every page to exactly one output file.");
    private final JLabel coverageLabel = new JLabel("Coverage: 0 of 0 pages assigned");
    private final JLabel unassignedLabel = new JLabel("Unassigned pages: none");
    private final JLabel duplicateLabel = new JLabel("Duplicate pages: none");
    private final JLabel statusLabel = new JLabel("Load a PDF to start splitting.");
    private final JTextArea issuesArea = new JTextArea();
    private final JButton openSourceButton = new JButton("Open PDF");
    private final JButton addGroupButton = new JButton("Add Output File");
    private final JButton removeGroupButton = new JButton("Remove Selected");
    private final JButton exportButton = new JButton("Export All");

    private Path sourcePath;
    private Path lastDirectory;
    private int sourcePageCount;
    private boolean uiBusy;
    private SplitValidationResult validationResult = new SplitValidationResult(0, 0, List.of(), List.of(), List.of());

    SplitPanel() {
        setLayout(new BorderLayout(0, 18));
        setBorder(new EmptyBorder(18, 22, 22, 22));
        setBackground(new Color(246, 249, 253));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        configureTable();
        registerInteractions();
        refreshValidation();
    }

    private JComponent buildHeader() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Split");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));

        JLabel subtitle = new JLabel("Allocate pages from one PDF into multiple output files and catch missing pages before export.");
        subtitle.setForeground(new Color(84, 96, 115));
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        wrapper.add(title);
        wrapper.add(subtitle);
        return wrapper;
    }

    private JComponent buildBody() {
        JPanel body = new JPanel(new BorderLayout(18, 0));
        body.setOpaque(false);
        body.add(buildGroupsCard(), BorderLayout.CENTER);
        body.add(buildValidationCard(), BorderLayout.EAST);
        return body;
    }

    private JComponent buildGroupsCard() {
        JPanel card = buildCard();
        card.setLayout(new BorderLayout(0, 14));

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(buildSourceCard(), BorderLayout.NORTH);
        top.add(buildGroupToolbar(), BorderLayout.SOUTH);

        JScrollPane scrollPane = new JScrollPane(groupTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(216, 225, 236)));
        scrollPane.getViewport().setBackground(Color.WHITE);

        JLabel helper = new JLabel("Page format: 1-3, 5, 8-10");
        helper.setForeground(new Color(84, 96, 115));

        card.add(top, BorderLayout.NORTH);
        card.add(scrollPane, BorderLayout.CENTER);
        card.add(helper, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildSourceCard() {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setOpaque(false);

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Source PDF");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));

        sourceNameLabel.setFont(sourceNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        sourceMetaLabel.setForeground(new Color(84, 96, 115));

        labels.add(title);
        labels.add(Box.createVerticalStrut(6));
        labels.add(sourceNameLabel);
        labels.add(Box.createVerticalStrut(2));
        labels.add(sourceMetaLabel);

        card.add(labels, BorderLayout.CENTER);
        card.add(openSourceButton, BorderLayout.EAST);
        return card;
    }

    private JComponent buildGroupToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.add(addGroupButton);
        left.add(Box.createHorizontalStrut(8));
        left.add(removeGroupButton);

        toolbar.add(left, BorderLayout.WEST);
        toolbar.add(exportButton, BorderLayout.EAST);
        return toolbar;
    }

    private JComponent buildValidationCard() {
        JPanel card = buildCard();
        card.setPreferredSize(new Dimension(320, 0));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Allocation Check");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        coverageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        coverageLabel.setFont(coverageLabel.getFont().deriveFont(Font.BOLD, 14f));

        unassignedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        duplicateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel issuesTitle = new JLabel("Issues");
        issuesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        issuesTitle.setFont(issuesTitle.getFont().deriveFont(Font.BOLD, 13f));

        issuesArea.setEditable(false);
        issuesArea.setFocusable(false);
        issuesArea.setLineWrap(true);
        issuesArea.setWrapStyleWord(true);
        issuesArea.setOpaque(false);
        issuesArea.setBorder(BorderFactory.createEmptyBorder());
        issuesArea.setForeground(new Color(84, 96, 115));
        issuesArea.setText("No issues.");

        card.add(title);
        card.add(Box.createVerticalStrut(14));
        card.add(coverageLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(unassignedLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(duplicateLabel);
        card.add(Box.createVerticalStrut(18));
        card.add(issuesTitle);
        card.add(Box.createVerticalStrut(8));
        card.add(issuesArea);
        card.add(Box.createVerticalGlue());

        return card;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        statusLabel.setForeground(new Color(84, 96, 115));
        footer.add(statusLabel, BorderLayout.WEST);
        return footer;
    }

    private JPanel buildCard() {
        JPanel card = new JPanel();
        card.setBackground(new Color(255, 255, 255, 225));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(216, 225, 236)),
                BorderFactory.createEmptyBorder(14, 16, 16, 16)
        ));
        return card;
    }

    private void configureTable() {
        groupTable.setFillsViewportHeight(true);
        groupTable.setRowHeight(30);
        groupTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        groupTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        groupTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        groupTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        groupTable.getColumnModel().getColumn(2).setPreferredWidth(50);
        groupTable.getColumnModel().getColumn(3).setPreferredWidth(280);
        groupTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(new JTextField()));
        groupTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JTextField()));

        DefaultTableCellRenderer renderer = new GroupCellRenderer();
        DefaultTableCellRenderer centeredRenderer = new GroupCellRenderer();
        centeredRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        groupTable.setDefaultRenderer(String.class, renderer);
        groupTable.getColumnModel().getColumn(2).setCellRenderer(centeredRenderer);
        groupTable.getColumnModel().getColumn(3).setCellRenderer(renderer);
    }

    private void registerInteractions() {
        openSourceButton.addActionListener(event -> chooseSourcePdf());
        addGroupButton.addActionListener(event -> addGroup());
        removeGroupButton.addActionListener(event -> removeSelectedGroups());
        exportButton.addActionListener(event -> exportGroups());

        groupTable.getSelectionModel().addListSelectionListener(event -> updateActionState());
        tableModel.addTableModelListener(event -> {
            if (tableModel.isDerivedUpdateInProgress()) {
                return;
            }
            if (event.getType() == TableModelEvent.UPDATE || event.getType() == TableModelEvent.INSERT || event.getType() == TableModelEvent.DELETE) {
                refreshValidation();
            }
        });
    }

    private void chooseSourcePdf() {
        FileDialog dialog = new FileDialog(SwingUtilities.getWindowAncestor(this) instanceof java.awt.Frame frame ? frame : null, "Choose Source PDF", FileDialog.LOAD);
        dialog.setMultipleMode(false);
        dialog.setFile("*.pdf");
        if (lastDirectory != null) {
            dialog.setDirectory(lastDirectory.toString());
        }
        dialog.setVisible(true);

        File selectedFile = dialog.getFiles().length > 0 ? dialog.getFiles()[0] : null;
        if (selectedFile == null) {
            return;
        }

        loadSourcePdf(selectedFile.toPath());
    }

    private void loadSourcePdf(Path path) {
        setBusy(true, "Loading source PDF...");

        SwingWorker<SourceDetails, Void> worker = new SwingWorker<>() {
            @Override
            protected SourceDetails doInBackground() throws Exception {
                long fileSize = Files.size(path);
                try (PDDocument document = Loader.loadPDF(path.toFile())) {
                    if (document.getNumberOfPages() <= 0) {
                        throw new IOException("The PDF has no pages.");
                    }
                    return new SourceDetails(path.toAbsolutePath(), document.getNumberOfPages(), fileSize);
                }
            }

            @Override
            protected void done() {
                try {
                    SourceDetails details = get();
                    sourcePath = details.path();
                    lastDirectory = details.path().getParent();
                    sourcePageCount = details.pageCount();
                    sourceNameLabel.setText(details.path().getFileName().toString());
                    sourceMetaLabel.setText(details.pageCount() + (details.pageCount() == 1 ? " page" : " pages") + "  •  " + formatFileSize(details.fileSize()));
                    setBusy(false, "Source PDF loaded. Assign every page before exporting.");
                    refreshValidation();
                } catch (Exception exception) {
                    sourcePath = null;
                    sourcePageCount = 0;
                    sourceNameLabel.setText("No source PDF selected");
                    sourceMetaLabel.setText(classifyLoadFailure(exception));
                    setBusy(false, "Could not load source PDF.");
                    refreshValidation();
                }
            }
        };

        worker.execute();
    }

    private void addGroup() {
        tableModel.addGroup(new SplitGroup("File " + (tableModel.getRowCount() + 1), ""));
        int newRow = tableModel.getRowCount() - 1;
        if (newRow >= 0) {
            groupTable.getSelectionModel().setSelectionInterval(newRow, newRow);
            groupTable.editCellAt(newRow, 1);
        }
        updateActionState();
    }

    private void removeSelectedGroups() {
        int[] rows = groupTable.getSelectedRows();
        if (rows.length == 0) {
            return;
        }

        if (groupTable.isEditing()) {
            groupTable.getCellEditor().stopCellEditing();
        }

        tableModel.removeRows(rows);
        refreshValidation();
    }

    private void exportGroups() {
        if (groupTable.isEditing()) {
            groupTable.getCellEditor().stopCellEditing();
        }

        refreshValidation();
        if (!validationResult.canExport(tableModel.getGroups(), sourcePath != null)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Every page must be assigned exactly once before export.",
                    "Cannot export",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        JFileChooser chooser = new JFileChooser(lastDirectory == null ? null : lastDirectory.toFile());
        chooser.setDialogTitle("Choose Output Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }

        Path outputDirectory = chooser.getSelectedFile().toPath();
        List<Path> conflictingPaths = collectExistingOutputs(outputDirectory);
        if (!conflictingPaths.isEmpty()) {
            String conflicts = conflictingPaths.stream()
                    .map(path -> "- " + path.getFileName())
                    .collect(Collectors.joining("\n"));

            int overwrite = JOptionPane.showConfirmDialog(
                    this,
                    "The following files already exist and will be replaced:\n\n" + conflicts,
                    "Replace existing files?",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (overwrite != JOptionPane.OK_OPTION) {
                return;
            }
        }

        setBusy(true, "Exporting split PDFs...");
        List<SplitGroup> groupsToExport = new ArrayList<>(tableModel.getGroups());
        SwingWorker<List<Path>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Path> doInBackground() throws Exception {
                return SplitExporter.export(sourcePath, groupsToExport, outputDirectory);
            }

            @Override
            protected void done() {
                try {
                    List<Path> exportedPaths = get();
                    lastDirectory = outputDirectory;
                    setBusy(false, exportedPaths.size() == 1
                            ? "Exported 1 split PDF."
                            : "Exported " + exportedPaths.size() + " split PDFs.");
                    JOptionPane.showMessageDialog(
                            SplitPanel.this,
                            exportedPaths.stream()
                                    .map(path -> path.getFileName().toString())
                                    .collect(Collectors.joining("\n")),
                            "Split export complete",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception exception) {
                    setBusy(false, "Split export failed.");
                    JOptionPane.showMessageDialog(
                            SplitPanel.this,
                            exception.getCause() == null ? exception.getMessage() : exception.getCause().getMessage(),
                            "Export failed",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };
        worker.execute();
    }

    private List<Path> collectExistingOutputs(Path outputDirectory) {
        List<Path> existing = new ArrayList<>();
        for (SplitGroup group : tableModel.getGroups()) {
            Path candidate = outputDirectory.resolve(SplitExporter.ensurePdfExtension(group.getOutputName()));
            if (Files.exists(candidate)) {
                existing.add(candidate);
            }
        }
        return existing;
    }

    private void refreshValidation() {
        validationResult = SplitAllocator.validate(sourcePageCount, tableModel.getGroups());
        tableModel.refreshDerivedColumns();
        groupTable.repaint();

        coverageLabel.setText("Coverage: " + validationResult.getAssignedUniquePages() + " of " + validationResult.getTotalPages() + " pages assigned");
        unassignedLabel.setText("Unassigned pages: " + SplitAllocator.formatPageList(validationResult.getUnassignedPages()));
        duplicateLabel.setText("Duplicate pages: " + SplitAllocator.formatPageList(validationResult.getDuplicatePages()));

        List<String> issues = new ArrayList<>(validationResult.getGeneralIssues());
        for (int index = 0; index < tableModel.getRowCount(); index++) {
            SplitGroup group = tableModel.getGroupAt(index);
            if (group.hasValidationIssue()) {
                issues.add(group.getOutputName().isBlank()
                        ? "Row " + (index + 1) + ": " + group.getValidationMessage().replace('\n', ';')
                        : group.getOutputName() + ": " + group.getValidationMessage().replace('\n', ';'));
            }
        }

        if (issues.isEmpty()) {
            issuesArea.setForeground(new Color(34, 95, 54));
            issuesArea.setText("No issues. Every page is allocated exactly once.");
        } else {
            issuesArea.setForeground(new Color(132, 27, 27));
            issuesArea.setText(String.join("\n", issues));
        }

        updateActionState();
    }

    private void setBusy(boolean busy, String message) {
        uiBusy = busy;
        updateActionState();
        statusLabel.setText(message);
        setCursor(busy ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR) : java.awt.Cursor.getDefaultCursor());
    }

    private void updateActionState() {
        boolean hasSource = sourcePath != null;
        boolean hasSelection = groupTable.getSelectedRowCount() > 0;
        openSourceButton.setEnabled(!uiBusy);
        addGroupButton.setEnabled(!uiBusy && hasSource);
        removeGroupButton.setEnabled(!uiBusy && hasSelection);
        exportButton.setEnabled(!uiBusy && validationResult.canExport(tableModel.getGroups(), hasSource));
        groupTable.setEnabled(!uiBusy);
    }

    private String classifyLoadFailure(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        if (cause instanceof InvalidPasswordException) {
            return "Password-protected PDFs are not supported here.";
        }
        if (cause instanceof AccessDeniedException) {
            return "Permission denied while reading the PDF.";
        }
        if (cause instanceof IOException ioException) {
            String message = ioException.getMessage() == null ? "" : ioException.getMessage().toLowerCase();
            if (message.contains("header") || message.contains("xref") || message.contains("trailer")) {
                return "The selected file is not a valid PDF.";
            }
            return ioException.getMessage();
        }
        return "Could not read that PDF.";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unitIndex = -1;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        return String.format(unitIndex > 0 ? "%.1f %s" : "%.0f %s", value, units[unitIndex]);
    }

    private static final class SourceDetails {
        private final Path path;
        private final int pageCount;
        private final long fileSize;

        private SourceDetails(Path path, int pageCount, long fileSize) {
            this.path = path;
            this.pageCount = pageCount;
            this.fileSize = fileSize;
        }

        Path path() {
            return path;
        }

        int pageCount() {
            return pageCount;
        }

        long fileSize() {
            return fileSize;
        }
    }

    private final class GroupCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
        ) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            SplitGroup group = tableModel.getGroupAt(row);
            setVerticalAlignment(TOP);
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            if (value instanceof String text && text.contains("\n")) {
                setText("<html>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>") + "</html>");
            }

            if (isSelected) {
                setBackground(group.hasValidationIssue() ? new Color(255, 233, 233) : new Color(228, 239, 255));
            } else {
                setBackground(group.hasValidationIssue() ? new Color(255, 245, 245) : Color.WHITE);
            }

            setForeground(group.hasValidationIssue() && column == 3 ? new Color(132, 27, 27) : new Color(36, 58, 92));
            return this;
        }
    }
}
