package com.ameli.pdfstitcher;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

final class PdfStitcherFrame extends JFrame {
    private static final String PREF_LAST_DIRECTORY = "lastDirectory";
    private static final String PREF_SESSION_ROWS = "sessionRows";

    private final Preferences preferences = Preferences.userNodeForPackage(PdfStitcherFrame.class);
    private final ExecutorService thumbnailExecutor = new ThreadPoolExecutor(
            2,
            2,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32),
            new TileWorkerThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final DefaultListModel<PdfEntry> listModel = new DefaultListModel<>();
    private final PdfTileList pdfList = new PdfTileList(listModel);
    private final JPanel centerPanel = new JPanel(new BorderLayout());
    private final JScrollPane listScrollPane = new JScrollPane(pdfList);
    private final JLabel emptyStateLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("Starting PDF-StitchUI...");
    private final JButton addButton = new JButton();
    private final JButton addFolderButton = new JButton();
    private final JButton duplicateButton = new JButton();
    private final JButton pageRangeButton = new JButton();
    private final JButton rotateLeftButton = new JButton();
    private final JButton rotateRightButton = new JButton();
    private final JButton moveLeftButton = new JButton();
    private final JButton moveRightButton = new JButton();
    private final JButton removeSelectedButton = new JButton();
    private final JButton clearAllButton = new JButton();
    private final JButton exportButton = new JButton();
    private final TransferHandler fileImportHandler = new FileImportTransferHandler();
    private final SplitPanel splitPanel = new SplitPanel();

    private boolean uiBusy;
    private Path lastDirectory;

    PdfStitcherFrame() {
        super("PDF-StitchUI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 820));
        setPreferredSize(new Dimension(1360, 900));

        buildFrame();
        setJMenuBar(buildMenuBar());
        registerInteractions();
        refreshState();

        pack();
        setLocationRelativeTo(null);
    }

    void restoreSessionAsync() {
        String storedDirectory = preferences.get(PREF_LAST_DIRECTORY, null);
        if (storedDirectory != null && !storedDirectory.isBlank()) {
            lastDirectory = Path.of(storedDirectory);
        }

        String storedRows = preferences.get(PREF_SESSION_ROWS, "");
        if (storedRows.isBlank()) {
            setBusy(false, "Add PDF files, add a folder, or drag them into the window.");
            return;
        }

        List<SessionRow> rows = storedRows.lines()
                .map(SessionRow::parse)
                .filter(row -> row != null && Files.exists(row.path()))
                .toList();

        if (rows.isEmpty()) {
            setBusy(false, "Add PDF files, add a folder, or drag them into the window.");
            return;
        }

        setBusy(true, "Restoring last session...");
        SwingUtilities.invokeLater(() -> {
            addSessionRows(rows, listModel.size());
            setBusy(false, rows.size() == 1
                    ? "Restored 1 tile from the last session."
                    : "Restored " + rows.size() + " tiles from the last session.");
        });
    }

    void activateWindow() {
        if (!isVisible()) {
            return;
        }

        if ((getExtendedState() & JFrame.ICONIFIED) != 0) {
            setExtendedState(JFrame.NORMAL);
        }

        toFront();
        requestFocus();
        setAlwaysOnTop(true);
        Timer timer = new Timer(1200, event -> setAlwaysOnTop(false));
        timer.setRepeats(false);
        timer.start();
    }

    private void buildFrame() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBorder(new EmptyBorder(18, 22, 22, 22));
        root.setBackground(new Color(246, 249, 253));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Stitch", buildStitchPanel());
        tabbedPane.addTab("Split", splitPanel);
        root.add(tabbedPane, BorderLayout.CENTER);

        setContentPane(root);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu toolsMenu = new JMenu("Tools");

        JCheckBoxMenuItem developerLoggingItem = new JCheckBoxMenuItem("Developer mode logging");
        developerLoggingItem.setSelected(AppDiagnostics.isDeveloperLoggingEnabled());
        developerLoggingItem.addActionListener(event -> {
            boolean enabled = developerLoggingItem.isSelected();
            AppDiagnostics.setDeveloperLoggingEnabled(enabled);
            statusLabel.setText(enabled
                    ? "Developer logging enabled. Troubleshooting logs will be written to disk."
                    : "Developer logging disabled.");
        });

        JMenuItem openLogFolderItem = new JMenuItem("Open log folder");
        openLogFolderItem.addActionListener(event -> {
            try {
                AppDiagnostics.openLogDirectory();
            } catch (IOException exception) {
                AppDiagnostics.error("Failed to open log folder.", exception);
                JOptionPane.showMessageDialog(
                        this,
                        "Unable to open the log folder.\n\n" + exception.getMessage(),
                        "Open log folder failed",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        toolsMenu.add(developerLoggingItem);
        toolsMenu.add(openLogFolderItem);
        menuBar.add(toolsMenu);
        return menuBar;
    }

    private JComponent buildStitchPanel() {
        JPanel stitchPanel = new JPanel(new BorderLayout(0, 18));
        stitchPanel.setOpaque(false);
        stitchPanel.add(buildHeader(), BorderLayout.NORTH);
        stitchPanel.add(buildCenterPanel(), BorderLayout.CENTER);
        stitchPanel.add(buildFooter(), BorderLayout.SOUTH);
        return stitchPanel;
    }

    private JComponent buildHeader() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("PDF-StitchUI");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));

        JLabel subtitle = new JLabel("Tile, tune, reorder, and export PDFs with page ranges, rotation, and save-time options.");
        subtitle.setForeground(new Color(84, 96, 115));
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 16, 0));

        configureActionButton(addButton, "Add PDF files", "Choose one or more PDF files to add to the collection.", ToolbarGlyph.ADD_FILE, false);
        configureActionButton(addFolderButton, "Add folder", "Import every PDF found in a folder, including nested folders.", ToolbarGlyph.ADD_FOLDER, false);
        configureActionButton(duplicateButton, "Duplicate selected", "Create another copy of the selected tile and keep its current settings.", ToolbarGlyph.DUPLICATE, false);
        configureActionButton(pageRangeButton, "Set page range", "Limit the selected tile to specific pages like 1-3, 7.", ToolbarGlyph.PAGE_RANGE, false);
        configureActionButton(rotateLeftButton, "Rotate left", "Rotate the selected tile 90 degrees counterclockwise.", ToolbarGlyph.ROTATE_LEFT, false);
        configureActionButton(rotateRightButton, "Rotate right", "Rotate the selected tile 90 degrees clockwise.", ToolbarGlyph.ROTATE_RIGHT, false);
        configureActionButton(moveLeftButton, "Move earlier", "Move the selected tile earlier in the sequence.", ToolbarGlyph.MOVE_LEFT, false);
        configureActionButton(moveRightButton, "Move later", "Move the selected tile later in the sequence.", ToolbarGlyph.MOVE_RIGHT, false);
        configureActionButton(removeSelectedButton, "Remove selected", "Remove the selected tiles from the collection.", ToolbarGlyph.REMOVE, false);
        configureActionButton(clearAllButton, "Clear all", "Remove every tile from the current collection.", ToolbarGlyph.CLEAR_ALL, false);
        configureActionButton(exportButton, "Export stitched PDF", "Build the stitched PDF and open the Save As dialog.", ToolbarGlyph.EXPORT, true);

        JPanel actionStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actionStrip.setOpaque(false);
        actionStrip.add(buildActionGroup("Import", addButton, addFolderButton));
        actionStrip.add(buildActionGroup("Adjust", duplicateButton, pageRangeButton, rotateLeftButton, rotateRightButton));
        actionStrip.add(buildActionGroup("Sequence", moveLeftButton, moveRightButton));
        actionStrip.add(buildActionGroup("Cleanup", removeSelectedButton, clearAllButton));
        actionStrip.add(buildActionGroup("Export", exportButton));

        wrapper.add(title);
        wrapper.add(subtitle);
        wrapper.add(actionStrip);
        return wrapper;
    }

    private JPanel buildActionGroup(String title, JButton... buttons) {
        JPanel group = new JPanel();
        group.setOpaque(true);
        group.setBackground(new Color(255, 255, 255, 225));
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(216, 225, 236)),
                BorderFactory.createEmptyBorder(10, 12, 12, 12)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(new Color(66, 79, 101));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (JButton button : buttons) {
            row.add(button);
        }

        group.add(titleLabel);
        group.add(Box.createVerticalStrut(10));
        group.add(row);
        return group;
    }

    private void configureActionButton(JButton button, String accessibleName, String tooltip, ToolbarGlyph glyph, boolean accent) {
        button.setFocusable(false);
        button.setText(null);
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(accessibleName);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setVerticalAlignment(SwingConstants.CENTER);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(44, 44));
        button.setMinimumSize(new Dimension(44, 44));
        button.setMaximumSize(new Dimension(44, 44));
        button.setOpaque(true);
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBackground(accent ? new Color(35, 107, 216) : Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent ? new Color(26, 86, 182) : new Color(203, 214, 229)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        button.setIcon(createToolbarIcon(glyph, accent ? Color.WHITE : new Color(54, 77, 111)));
        button.setDisabledIcon(createToolbarIcon(glyph, new Color(165, 174, 189)));
    }

    private Icon createToolbarIcon(ToolbarGlyph glyph, Color color) {
        return new ToolbarIcon(glyph, color);
    }

    private JComponent buildCenterPanel() {
        pdfList.setCellRenderer(new PdfTileRenderer());
        pdfList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        pdfList.setVisibleRowCount(-1);
        pdfList.setFixedCellWidth(PdfTileRenderer.CELL_WIDTH);
        pdfList.setFixedCellHeight(PdfTileRenderer.CELL_HEIGHT);
        pdfList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        pdfList.setOpaque(false);
        pdfList.setBackground(new Color(246, 249, 253));
        pdfList.setSelectionBackground(new Color(246, 249, 253));
        pdfList.setDragEnabled(true);
        pdfList.setDropMode(javax.swing.DropMode.INSERT);
        pdfList.setTransferHandler(new PdfListTransferHandler());
        pdfList.setBorder(BorderFactory.createEmptyBorder(8, 2, 8, 2));

        listScrollPane.setBorder(BorderFactory.createEmptyBorder());
        listScrollPane.setOpaque(false);
        listScrollPane.getViewport().setOpaque(false);
        listScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        emptyStateLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyStateLabel.setFont(emptyStateLabel.getFont().deriveFont(Font.PLAIN, 17f));
        emptyStateLabel.setForeground(new Color(99, 109, 126));
        emptyStateLabel.setText("""
                <html><div style='text-align:center;'>
                No PDFs yet.<br/>
                Use <b>Add PDFs</b>, <b>Add Folder</b>, or drag PDF files and folders into this window.
                </div></html>
                """);

        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(216, 225, 236)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        centerPanel.setTransferHandler(fileImportHandler);
        emptyStateLabel.setTransferHandler(fileImportHandler);
        listScrollPane.setTransferHandler(fileImportHandler);
        return centerPanel;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 4));
        statusLabel.setForeground(new Color(84, 96, 115));
        footer.add(statusLabel, BorderLayout.WEST);
        return footer;
    }

    private void registerInteractions() {
        addButton.addActionListener(event -> chooseAndAddPdfs());
        addFolderButton.addActionListener(event -> chooseAndAddFolder());
        duplicateButton.addActionListener(event -> duplicateSelectedEntries());
        pageRangeButton.addActionListener(event -> promptPageRangeForSelected());
        rotateLeftButton.addActionListener(event -> rotateSelectedEntries(-90));
        rotateRightButton.addActionListener(event -> rotateSelectedEntries(90));
        moveLeftButton.addActionListener(event -> moveSelectedEntries(-1));
        moveRightButton.addActionListener(event -> moveSelectedEntries(1));
        removeSelectedButton.addActionListener(event -> removeSelectedEntries());
        clearAllButton.addActionListener(event -> clearAllEntries());
        exportButton.addActionListener(event -> exportMergedPdf());

        pdfList.addListSelectionListener(event -> updateActionState());
        pdfList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                updateRemoveHoverState(event.getPoint(), true);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (updateRemoveHoverState(event.getPoint(), false)) {
                    int index = pdfList.locationToIndex(event.getPoint());
                    if (index >= 0) {
                        pdfList.setSelectedIndex(index);
                        removeSelectedEntries();
                    }
                } else {
                    pdfList.putClientProperty("removePressedIndex", -1);
                    pdfList.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                pdfList.putClientProperty("removeHoverIndex", -1);
                pdfList.putClientProperty("removePressedIndex", -1);
                pdfList.repaint();
            }
        });

        pdfList.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                boolean hoveringRemove = updateRemoveHoverState(event.getPoint(), false);
                pdfList.setCursor(hoveringRemove
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });

        pdfList.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "removeSelectedPdf");
        pdfList.getInputMap().put(KeyStroke.getKeyStroke("control O"), "choosePdfFiles");
        pdfList.getInputMap().put(KeyStroke.getKeyStroke("control shift O"), "choosePdfFolder");
        pdfList.getInputMap().put(KeyStroke.getKeyStroke("control D"), "duplicateSelected");
        pdfList.getInputMap().put(KeyStroke.getKeyStroke("alt LEFT"), "moveSelectedLeft");
        pdfList.getInputMap().put(KeyStroke.getKeyStroke("alt RIGHT"), "moveSelectedRight");
        pdfList.getInputMap().put(KeyStroke.getKeyStroke("control shift S"), "exportMergedPdf");

        pdfList.getActionMap().put("removeSelectedPdf", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                removeSelectedEntries();
            }
        });
        pdfList.getActionMap().put("choosePdfFiles", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                chooseAndAddPdfs();
            }
        });
        pdfList.getActionMap().put("choosePdfFolder", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                chooseAndAddFolder();
            }
        });
        pdfList.getActionMap().put("duplicateSelected", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                duplicateSelectedEntries();
            }
        });
        pdfList.getActionMap().put("moveSelectedLeft", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                moveSelectedEntries(-1);
            }
        });
        pdfList.getActionMap().put("moveSelectedRight", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                moveSelectedEntries(1);
            }
        });
        pdfList.getActionMap().put("exportMergedPdf", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                exportMergedPdf();
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                persistSession();
                thumbnailExecutor.shutdownNow();
            }
        });
    }

    private void chooseAndAddPdfs() {
        FileDialog dialog = new FileDialog(this, "Add PDF Files", FileDialog.LOAD);
        dialog.setMultipleMode(true);
        if (lastDirectory != null) {
            dialog.setDirectory(lastDirectory.toString());
        }
        dialog.setVisible(true);

        File[] files = dialog.getFiles();
        if (files == null || files.length == 0) {
            return;
        }

        List<Path> paths = new ArrayList<>();
        for (File file : files) {
            if (file != null) {
                paths.add(file.toPath());
            }
        }

        List<Path> pdfPaths = expandPaths(paths);
        if (pdfPaths.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No PDF files were found in the selected items.", "No PDFs found", JOptionPane.WARNING_MESSAGE);
            return;
        }

        lastDirectory = pdfPaths.getFirst().getParent();
        addPdfFiles(pdfPaths, listModel.size());
    }

    private void chooseAndAddFolder() {
        JFileChooser chooser = new JFileChooser(lastDirectory == null ? null : lastDirectory.toFile());
        chooser.setDialogTitle("Add Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }

        Path folder = chooser.getSelectedFile().toPath();
        List<Path> pdfPaths = expandPaths(List.of(folder));
        if (pdfPaths.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No PDF files were found in that folder.", "No PDFs found", JOptionPane.WARNING_MESSAGE);
            return;
        }

        lastDirectory = folder;
        addPdfFiles(pdfPaths, listModel.size());
    }

    private void addPdfFiles(List<Path> paths, int insertIndex) {
        List<SessionRow> rows = paths.stream()
                .map(path -> new SessionRow(path.toAbsolutePath(), "", 0))
                .toList();
        addSessionRows(rows, insertIndex);
    }

    private void addSessionRows(List<SessionRow> rows, int insertIndex) {
        int position = Math.max(0, Math.min(insertIndex, listModel.size()));
        for (SessionRow row : rows) {
            PdfEntry entry = new PdfEntry(row.path());
            entry.setPageRangeSpec(row.pageRangeSpec());
            while (entry.getRotationDegrees() != Math.floorMod(row.rotationDegrees(), 360)) {
                entry.rotateRight();
            }
            listModel.add(position++, entry);
            loadEntryDetails(entry);
        }
        refreshState();
        persistSession();
    }

    private void duplicateSelectedEntries() {
        int[] selectedIndices = pdfList.getSelectedIndices();
        if (selectedIndices.length == 0) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        int offset = 1;
        for (int index : selectedIndices) {
            PdfEntry copy = listModel.get(index).copy();
            listModel.add(index + offset, copy);
            offset++;
        }

        refreshState();
        statusLabel.setText(selectedIndices.length == 1 ? "Duplicated 1 tile." : "Duplicated " + selectedIndices.length + " tiles.");
        persistSession();
    }

    private void promptPageRangeForSelected() {
        List<PdfEntry> selectedEntries = pdfList.getSelectedValuesList();
        if (selectedEntries.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        if (selectedEntries.stream().anyMatch(PdfEntry::isLoading)) {
            JOptionPane.showMessageDialog(this, "Wait for the selected PDFs to finish loading before editing page ranges.", "PDFs still loading", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String defaultValue = selectedEntries.getFirst().getPageRangeSpec();
        String input = JOptionPane.showInputDialog(
                this,
                "Enter the page range to export for the selected tiles.\nExamples: 1-3, 5, 8-10\nLeave blank to use all pages.",
                defaultValue,
                JOptionPane.PLAIN_MESSAGE
        );

        if (input == null) {
            return;
        }

        String range = input.trim();
        for (PdfEntry entry : selectedEntries) {
            try {
                PageRangeParser.parse(range, entry.getTotalPages());
            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(
                        this,
                        "The range is invalid for " + entry.getDisplayName() + ".\n\n" + exception.getMessage(),
                        "Invalid page range",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }
        }

        for (PdfEntry entry : selectedEntries) {
            entry.setPageRangeSpec(range);
        }

        pdfList.repaint();
        statusLabel.setText(selectedEntries.size() == 1 ? "Updated page range for 1 tile." : "Updated page ranges for " + selectedEntries.size() + " tiles.");
        persistSession();
    }

    private void rotateSelectedEntries(int deltaDegrees) {
        List<PdfEntry> selectedEntries = pdfList.getSelectedValuesList();
        if (selectedEntries.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        for (PdfEntry entry : selectedEntries) {
            if (deltaDegrees < 0) {
                entry.rotateLeft();
            } else {
                entry.rotateRight();
            }
        }

        pdfList.repaint();
        statusLabel.setText(selectedEntries.size() == 1 ? "Updated rotation for 1 tile." : "Updated rotation for " + selectedEntries.size() + " tiles.");
        persistSession();
    }

    private void moveSelectedEntries(int direction) {
        int[] selectedIndices = pdfList.getSelectedIndices();
        if (selectedIndices.length == 0) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        List<PdfEntry> entries = toMutableList();
        boolean[] selected = new boolean[entries.size()];
        for (int index : selectedIndices) {
            selected[index] = true;
        }

        if (direction < 0) {
            for (int index = 0; index < entries.size(); index++) {
                if (selected[index] && index > 0 && !selected[index - 1]) {
                    Collections.swap(entries, index, index - 1);
                    selected[index - 1] = true;
                    selected[index] = false;
                }
            }
        } else {
            for (int index = entries.size() - 1; index >= 0; index--) {
                if (selected[index] && index < entries.size() - 1 && !selected[index + 1]) {
                    Collections.swap(entries, index, index + 1);
                    selected[index + 1] = true;
                    selected[index] = false;
                }
            }
        }

        int[] newSelection = new int[selectedIndices.length];
        int selectionCursor = 0;
        rebuildModel(entries);
        for (int index = 0; index < selected.length; index++) {
            if (selected[index]) {
                newSelection[selectionCursor++] = index;
            }
        }
        pdfList.setSelectedIndices(newSelection);
        statusLabel.setText("Moved the selected tiles " + (direction < 0 ? "left." : "right."));
        persistSession();
    }

    private void removeSelectedEntries() {
        int[] selectedIndices = pdfList.getSelectedIndices();
        if (selectedIndices.length == 0) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        for (int index = selectedIndices.length - 1; index >= 0; index--) {
            listModel.remove(selectedIndices[index]);
        }

        refreshState();
        statusLabel.setText(selectedIndices.length == 1 ? "Removed 1 tile." : "Removed " + selectedIndices.length + " tiles.");
        persistSession();
    }

    private void clearAllEntries() {
        if (listModel.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Remove every PDF tile from the current collection?",
                "Clear collection",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        listModel.clear();
        refreshState();
        statusLabel.setText("Cleared the collection.");
        persistSession();
    }

    private void exportMergedPdf() {
        if (listModel.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        List<String> blockedEntries = collectBlockedEntries();
        if (!blockedEntries.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "These tiles must be fixed before export:\n\n" + String.join("\n", blockedEntries),
                    "Export blocked",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        ExportOptions options = showExportOptionsDialog();
        if (options == null) {
            return;
        }

        Path destination = chooseExportDestination();
        if (destination == null) {
            return;
        }

        setBusy(true, "Exporting stitched PDF...");
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return writeExport(destination, options);
            }

            @Override
            protected void done() {
                try {
                    Path exported = get();
                    setBusy(false, "Exported stitched PDF to " + exported + ".");
                    offerToOpenExport(exported);
                } catch (Exception exception) {
                    setBusy(false, "Export failed.");
                    JOptionPane.showMessageDialog(
                            PdfStitcherFrame.this,
                            "Unable to export the stitched PDF.\n\n" + exception.getMessage(),
                            "Export failed",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    private ExportOptions showExportOptionsDialog() {
        JCheckBox bookmarksBox = new JCheckBox("Create bookmarks for each tile", true);
        JCheckBox flattenFormsBox = new JCheckBox("Flatten PDF forms before export", false);
        JCheckBox compressBox = new JCheckBox("Compress the output PDF", true);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Choose export settings:"));
        panel.add(Box.createVerticalStrut(8));
        panel.add(bookmarksBox);
        panel.add(Box.createVerticalStrut(6));
        panel.add(flattenFormsBox);
        panel.add(Box.createVerticalStrut(6));
        panel.add(compressBox);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Export settings",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        return new ExportOptions(bookmarksBox.isSelected(), flattenFormsBox.isSelected(), compressBox.isSelected());
    }

    private Path chooseExportDestination() {
        while (true) {
            FileDialog dialog = new FileDialog(this, "Export Stitched PDF", FileDialog.SAVE);
            dialog.setFile("stitched.pdf");
            if (lastDirectory != null) {
                dialog.setDirectory(lastDirectory.toString());
            }

            dialog.setVisible(true);
            if (dialog.getFile() == null) {
                return null;
            }

            Path destination = Path.of(dialog.getDirectory(), ensurePdfExtension(dialog.getFile()));
            lastDirectory = destination.getParent();

            if (!Files.exists(destination)) {
                return destination;
            }

            Object[] options = {"Replace", "Choose Another File", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                    this,
                    "The file already exists:\n" + destination + "\n\nChoose whether to replace it or pick a different name.",
                    "Confirm overwrite",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            if (choice == 0) {
                return destination;
            }
            if (choice != 1) {
                return null;
            }
        }
    }

    private Path writeExport(Path destination, ExportOptions options) throws IOException {
        AppDiagnostics.info("Starting stitched export to " + destination + ".");
        Path temporaryFile = Files.createTempFile(destination.getParent(), "pdf-stitchui-", ".pdf");

        try (PDDocument destinationDocument = new PDDocument()) {
            destinationDocument.setDocumentInformation(new PDDocumentInformation());
            destinationDocument.getDocumentInformation().setProducer("PDF-StitchUI");

            PDDocumentOutline outline = options.addBookmarks() ? new PDDocumentOutline() : null;
            if (outline != null) {
                destinationDocument.getDocumentCatalog().setDocumentOutline(outline);
            }

            for (int index = 0; index < listModel.size(); index++) {
                PdfEntry entry = listModel.get(index);
                importEntry(destinationDocument, outline, entry, options);
            }

            if (outline != null) {
                outline.openNode();
            }

            CompressParameters compression = options.compressOutput()
                    ? CompressParameters.DEFAULT_COMPRESSION
                    : CompressParameters.NO_COMPRESSION;
            destinationDocument.save(temporaryFile.toFile(), compression);
        } catch (IOException exception) {
            Files.deleteIfExists(temporaryFile);
            throw exception;
        }

        Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
        AppDiagnostics.info("Completed stitched export to " + destination + ".");
        return destination;
    }

    private void importEntry(PDDocument destinationDocument, PDDocumentOutline outline, PdfEntry entry, ExportOptions options) throws IOException {
        try (PDDocument sourceDocument = Loader.loadPDF(entry.getPath().toFile())) {
            if (options.flattenForms()) {
                PDAcroForm acroForm = sourceDocument.getDocumentCatalog().getAcroForm();
                if (acroForm != null) {
                    acroForm.flatten();
                }
            }

            PDPage bookmarkPage = null;
            for (Integer pageIndex : entry.getSelectedPages()) {
                PDPage sourcePage = sourceDocument.getPage(pageIndex);
                PDPage importedPage = destinationDocument.importPage(sourcePage);
                importedPage.setRotation(Math.floorMod(sourcePage.getRotation() + entry.getRotationDegrees(), 360));
                if (bookmarkPage == null) {
                    bookmarkPage = importedPage;
                }
            }

            if (outline != null && bookmarkPage != null) {
                PDPageFitDestination pageDestination = new PDPageFitDestination();
                pageDestination.setPage(bookmarkPage);

                PDOutlineItem item = new PDOutlineItem();
                item.setDestination(pageDestination);
                item.setTitle(buildBookmarkTitle(entry));
                outline.addLast(item);
            }
        }
    }

    private String buildBookmarkTitle(PdfEntry entry) {
        StringBuilder title = new StringBuilder(entry.getDisplayName());
        if (!entry.getPageRangeSpec().isBlank()) {
            title.append(" [").append(entry.getPageRangeSpec()).append(']');
        }
        if (entry.getRotationDegrees() != 0) {
            title.append(" (").append(entry.getRotationDegrees()).append("\u00b0)");
        }
        return title.toString();
    }

    private void loadEntryDetails(PdfEntry entry) {
        thumbnailExecutor.submit(() -> {
            long fileSize = -1L;
            try {
                fileSize = Files.size(entry.getPath());
                try (PDDocument document = Loader.loadPDF(entry.getPath().toFile())) {
                    if (document.getNumberOfPages() == 0) {
                        throw new IOException("The PDF has no pages.");
                    }

                    PDFRenderer renderer = new PDFRenderer(document);
                    BufferedImage preview = renderer.renderImageWithDPI(0, 120);
                    Image scaled = scaleToTile(preview);
                    boolean encrypted = document.isEncrypted();
                    long finalFileSize = fileSize;
                    SwingUtilities.invokeLater(() -> {
                        entry.applyLoadSuccess(new javax.swing.ImageIcon(scaled), document.getNumberOfPages(), finalFileSize, encrypted);
                        pdfList.repaint();
                        persistSession();
                    });
                }
            } catch (InvalidPasswordException exception) {
                long finalFileSize = fileSize;
                SwingUtilities.invokeLater(() -> {
                    entry.applyLoadFailure("Password-protected PDF", finalFileSize);
                    pdfList.repaint();
                    persistSession();
                });
            } catch (AccessDeniedException exception) {
                long finalFileSize = fileSize;
                SwingUtilities.invokeLater(() -> {
                    entry.applyLoadFailure("Permission denied", finalFileSize);
                    pdfList.repaint();
                    persistSession();
                });
            } catch (IOException exception) {
                String message = classifyIOException(exception);
                long finalFileSize = fileSize;
                SwingUtilities.invokeLater(() -> {
                    entry.applyLoadFailure(message, finalFileSize);
                    pdfList.repaint();
                    persistSession();
                });
            }
        });
    }

    private String classifyIOException(IOException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        if (message.contains("password")) {
            return "Password-protected PDF";
        }
        if (message.contains("permission")) {
            return "Permission denied";
        }
        if (message.contains("header") || message.contains("xref") || message.contains("trailer")) {
            return "Malformed PDF";
        }
        return "Unreadable PDF";
    }

    private Image scaleToTile(BufferedImage image) {
        int maxWidth = 180;
        int maxHeight = 188;
        double scale = Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight());
        scale = Math.min(scale, 1.0d);

        int scaledWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        return image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
    }

    private void refreshState() {
        centerPanel.removeAll();
        centerPanel.add(listModel.isEmpty() ? emptyStateLabel : listScrollPane, BorderLayout.CENTER);
        updateActionState();
        centerPanel.revalidate();
        centerPanel.repaint();
    }

    private void setBusy(boolean busy, String message) {
        uiBusy = busy;
        addButton.setEnabled(!busy);
        addFolderButton.setEnabled(!busy);
        duplicateButton.setEnabled(!busy && !pdfList.isSelectionEmpty());
        pageRangeButton.setEnabled(!busy && !pdfList.isSelectionEmpty());
        rotateLeftButton.setEnabled(!busy && !pdfList.isSelectionEmpty());
        rotateRightButton.setEnabled(!busy && !pdfList.isSelectionEmpty());
        moveLeftButton.setEnabled(!busy && !pdfList.isSelectionEmpty());
        moveRightButton.setEnabled(!busy && !pdfList.isSelectionEmpty());
        removeSelectedButton.setEnabled(!busy && !pdfList.isSelectionEmpty());
        clearAllButton.setEnabled(!busy && !listModel.isEmpty());
        exportButton.setEnabled(!busy && !listModel.isEmpty());
        pdfList.setEnabled(!busy);
        statusLabel.setText(message);
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    private void updateActionState() {
        boolean hasSelection = !pdfList.isSelectionEmpty();
        duplicateButton.setEnabled(!uiBusy && hasSelection);
        pageRangeButton.setEnabled(!uiBusy && hasSelection);
        rotateLeftButton.setEnabled(!uiBusy && hasSelection);
        rotateRightButton.setEnabled(!uiBusy && hasSelection);
        moveLeftButton.setEnabled(!uiBusy && hasSelection);
        moveRightButton.setEnabled(!uiBusy && hasSelection);
        removeSelectedButton.setEnabled(!uiBusy && hasSelection);
        clearAllButton.setEnabled(!uiBusy && !listModel.isEmpty());
        exportButton.setEnabled(!uiBusy && !listModel.isEmpty());
    }

    private boolean updateRemoveHoverState(Point point, boolean pressed) {
        int index = pdfList.locationToIndex(point);
        if (index < 0) {
            pdfList.putClientProperty("removeHoverIndex", -1);
            pdfList.putClientProperty("removePressedIndex", -1);
            pdfList.repaint();
            return false;
        }

        Rectangle bounds = pdfList.getCellBounds(index, index);
        boolean hoveringRemove = bounds != null && removeButtonBounds(bounds).contains(point);
        pdfList.putClientProperty("removeHoverIndex", hoveringRemove ? index : -1);
        pdfList.putClientProperty("removePressedIndex", pressed && hoveringRemove ? index : -1);
        pdfList.repaint();
        return hoveringRemove;
    }

    private Rectangle removeButtonBounds(Rectangle cellBounds) {
        return new Rectangle(
                cellBounds.x + PdfTileRenderer.removeButtonLeft(),
                cellBounds.y + PdfTileRenderer.removeButtonTop(),
                PdfTileRenderer.REMOVE_SIZE,
                PdfTileRenderer.REMOVE_SIZE
        );
    }

    private List<Path> expandPaths(List<Path> inputPaths) {
        LinkedHashSet<Path> results = new LinkedHashSet<>();
        for (Path inputPath : inputPaths) {
            if (Files.isDirectory(inputPath)) {
                try (Stream<Path> paths = Files.walk(inputPath)) {
                    paths.filter(Files::isRegularFile)
                            .filter(this::isPdf)
                            .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                            .map(Path::toAbsolutePath)
                            .forEach(results::add);
                } catch (IOException exception) {
                    statusLabel.setText("Could not read folder " + inputPath.getFileName() + ".");
                }
            } else if (Files.isRegularFile(inputPath) && isPdf(inputPath)) {
                results.add(inputPath.toAbsolutePath());
            }
        }
        return new ArrayList<>(results);
    }

    private boolean isPdf(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".pdf");
    }

    private String ensurePdfExtension(String fileName) {
        return fileName.toLowerCase().endsWith(".pdf") ? fileName : fileName + ".pdf";
    }

    private List<String> collectBlockedEntries() {
        List<String> blockedEntries = new ArrayList<>();
        for (int index = 0; index < listModel.size(); index++) {
            PdfEntry entry = listModel.get(index);
            if (!entry.canExport()) {
                blockedEntries.add("- " + entry.getDisplayName() + ": " + (entry.getBlockingIssue() == null ? "Still loading" : entry.getBlockingIssue()));
            }
        }
        return blockedEntries;
    }

    private void offerToOpenExport(Path exportedPath) {
        Object[] options = {"Open PDF", "Close"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Created stitched PDF:\n" + exportedPath,
                "Export complete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == JOptionPane.YES_OPTION
                && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            try {
                Desktop.getDesktop().open(exportedPath.toFile());
            } catch (IOException exception) {
                JOptionPane.showMessageDialog(
                        this,
                        "The PDF was saved, but it could not be opened automatically.\n\n" + exception.getMessage(),
                        "Open failed",
                        JOptionPane.WARNING_MESSAGE
                );
            }
        }
    }

    private void persistSession() {
        if (lastDirectory != null) {
            preferences.put(PREF_LAST_DIRECTORY, lastDirectory.toString());
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < listModel.size(); index++) {
            PdfEntry entry = listModel.get(index);
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(SessionRow.serialize(entry));
        }
        preferences.put(PREF_SESSION_ROWS, builder.toString());
    }

    private List<PdfEntry> toMutableList() {
        List<PdfEntry> entries = new ArrayList<>(listModel.size());
        for (int index = 0; index < listModel.size(); index++) {
            entries.add(listModel.get(index));
        }
        return entries;
    }

    private void rebuildModel(List<PdfEntry> entries) {
        listModel.clear();
        for (PdfEntry entry : entries) {
            listModel.addElement(entry);
        }
        refreshState();
    }

    private final class PdfListTransferHandler extends TransferHandler {
        private final DataFlavor localEntryFlavor;

        PdfListTransferHandler() {
            try {
                localEntryFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + DraggedEntry.class.getName());
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("Unable to configure drag-and-drop support.", exception);
            }
        }

        @Override
        public int getSourceActions(JComponent component) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent component) {
            int[] selectedIndices = pdfList.getSelectedIndices();
            if (selectedIndices.length != 1) {
                return null;
            }

            PdfEntry selected = pdfList.getModel().getElementAt(selectedIndices[0]);
            return new SimpleTransferable(new DraggedEntry(selected, selectedIndices[0]), localEntryFlavor);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                support.setShowDropLocation(true);
                pdfList.setDropInsertionIndex(resolveDropIndex(support));
                return true;
            }

            boolean supportsReorder = support.isDrop() && support.isDataFlavorSupported(localEntryFlavor);
            if (supportsReorder) {
                support.setShowDropLocation(true);
                pdfList.setDropInsertionIndex(resolveDropIndex(support));
            } else {
                pdfList.clearDropInsertionIndex();
            }
            return supportsReorder;
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    @SuppressWarnings("unchecked")
                    List<File> droppedFiles = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    List<Path> pdfPaths = expandPaths(droppedFiles.stream().map(File::toPath).toList());
                    if (pdfPaths.isEmpty()) {
                        return false;
                    }

                    int index = resolveDropIndex(support);
                    lastDirectory = pdfPaths.getFirst().getParent();
                    addPdfFiles(pdfPaths, index);
                    return true;
                }

                if (support.isDataFlavorSupported(localEntryFlavor)) {
                    DraggedEntry draggedEntry = (DraggedEntry) support.getTransferable().getTransferData(localEntryFlavor);
                    int targetIndex = resolveDropIndex(support);
                    if (targetIndex > listModel.size()) {
                        targetIndex = listModel.size();
                    }
                    if (draggedEntry.sourceIndex() < targetIndex) {
                        targetIndex--;
                    }
                    if (targetIndex == draggedEntry.sourceIndex()) {
                        return false;
                    }

                    PdfEntry entry = listModel.remove(draggedEntry.sourceIndex());
                    listModel.add(targetIndex, entry);
                    pdfList.setSelectedIndex(targetIndex);
                    refreshState();
                    statusLabel.setText("Moved " + draggedEntry.entry().getDisplayName() + " to sequence " + (targetIndex + 1) + ".");
                    persistSession();
                    return true;
                }
            } catch (UnsupportedFlavorException | IOException exception) {
                UIManager.getLookAndFeel().provideErrorFeedback(PdfStitcherFrame.this);
            } finally {
                pdfList.clearDropInsertionIndex();
            }
            return false;
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            pdfList.clearDropInsertionIndex();
            super.exportDone(source, data, action);
        }

        private int resolveDropIndex(TransferSupport support) {
            if (support.getDropLocation() instanceof JList.DropLocation location) {
                return location.getIndex();
            }

            Point point = support.getDropLocation().getDropPoint();
            int index = pdfList.locationToIndex(point);
            return index >= 0 ? index : listModel.size();
        }
    }

    private final class FileImportTransferHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                @SuppressWarnings("unchecked")
                List<File> droppedFiles = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                List<Path> pdfPaths = expandPaths(droppedFiles.stream().map(File::toPath).toList());
                if (pdfPaths.isEmpty()) {
                    return false;
                }

                lastDirectory = pdfPaths.getFirst().getParent();
                addPdfFiles(pdfPaths, listModel.size());
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                UIManager.getLookAndFeel().provideErrorFeedback(PdfStitcherFrame.this);
                return false;
            }
        }
    }

    private record DraggedEntry(PdfEntry entry, int sourceIndex) {
    }

    private record ExportOptions(boolean addBookmarks, boolean flattenForms, boolean compressOutput) {
    }

    private record SessionRow(Path path, String pageRangeSpec, int rotationDegrees) {
        static SessionRow parse(String row) {
            String[] parts = row.split("\t", -1);
            if (parts.length < 3) {
                return null;
            }
            try {
                return new SessionRow(Path.of(parts[0]), parts[1], Integer.parseInt(parts[2]));
            } catch (RuntimeException exception) {
                return null;
            }
        }

        static String serialize(PdfEntry entry) {
            return entry.getPath() + "\t" + entry.getPageRangeSpec() + "\t" + entry.getRotationDegrees();
        }
    }

    private static final class SimpleTransferable implements Transferable {
        private final Object value;
        private final DataFlavor flavor;

        private SimpleTransferable(Object value, DataFlavor flavor) {
            this.value = value;
            this.flavor = flavor;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{flavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor candidateFlavor) {
            return flavor.equals(candidateFlavor);
        }

        @Override
        public Object getTransferData(DataFlavor candidateFlavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(candidateFlavor)) {
                throw new UnsupportedFlavorException(candidateFlavor);
            }
            return value;
        }
    }

    private static final class TileWorkerThreadFactory implements ThreadFactory {
        private int threadCount = 1;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "pdf-stitchui-worker-" + threadCount++);
            thread.setDaemon(true);
            return thread;
        }
    }

    private enum ToolbarGlyph {
        ADD_FILE,
        ADD_FOLDER,
        DUPLICATE,
        PAGE_RANGE,
        ROTATE_LEFT,
        ROTATE_RIGHT,
        MOVE_LEFT,
        MOVE_RIGHT,
        REMOVE,
        CLEAR_ALL,
        EXPORT
    }

    private static final class ToolbarIcon implements Icon {
        private final ToolbarGlyph glyph;
        private final Color color;

        private ToolbarIcon(ToolbarGlyph glyph, Color color) {
            this.glyph = glyph;
            this.color = color;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.translate(x, y);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (glyph) {
                case ADD_FILE -> paintAddFile(g2);
                case ADD_FOLDER -> paintAddFolder(g2);
                case DUPLICATE -> paintDuplicate(g2);
                case PAGE_RANGE -> paintPageRange(g2);
                case ROTATE_LEFT -> paintRotateLeft(g2);
                case ROTATE_RIGHT -> paintRotateRight(g2);
                case MOVE_LEFT -> paintMoveLeft(g2);
                case MOVE_RIGHT -> paintMoveRight(g2);
                case REMOVE -> paintRemove(g2);
                case CLEAR_ALL -> paintClearAll(g2);
                case EXPORT -> paintExport(g2);
            }

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }

        private void paintAddFile(Graphics2D g2) {
            g2.drawRoundRect(3, 2, 12, 16, 3, 3);
            g2.drawLine(9, 6, 9, 14);
            g2.drawLine(5, 10, 13, 10);
            g2.drawLine(11, 2, 15, 6);
        }

        private void paintAddFolder(Graphics2D g2) {
            g2.drawRoundRect(2, 6, 16, 10, 3, 3);
            g2.drawLine(4, 6, 7, 3);
            g2.drawLine(7, 3, 11, 3);
            g2.drawLine(12, 9, 12, 15);
            g2.drawLine(9, 12, 15, 12);
        }

        private void paintDuplicate(Graphics2D g2) {
            g2.drawRoundRect(5, 3, 10, 12, 3, 3);
            g2.drawRoundRect(2, 6, 10, 12, 3, 3);
        }

        private void paintPageRange(Graphics2D g2) {
            g2.drawRoundRect(3, 2, 14, 16, 3, 3);
            g2.drawLine(6, 7, 14, 7);
            g2.drawLine(6, 11, 10, 11);
            g2.drawLine(12, 11, 14, 11);
            g2.drawLine(6, 15, 14, 15);
        }

        private void paintRotateLeft(Graphics2D g2) {
            g2.drawRoundRect(9, 3, 7, 11, 2, 2);
            g2.drawArc(1, 5, 12, 12, 35, 235);
            g2.drawLine(2, 10, 2, 5);
            g2.drawLine(2, 10, 7, 10);
        }

        private void paintRotateRight(Graphics2D g2) {
            g2.drawRoundRect(4, 3, 7, 11, 2, 2);
            g2.drawArc(7, 5, 12, 12, 270, 235);
            g2.drawLine(18, 10, 18, 5);
            g2.drawLine(13, 10, 18, 10);
        }

        private void paintMoveLeft(Graphics2D g2) {
            g2.drawLine(17, 10, 5, 10);
            g2.drawLine(9, 6, 5, 10);
            g2.drawLine(9, 14, 5, 10);
        }

        private void paintMoveRight(Graphics2D g2) {
            g2.drawLine(3, 10, 15, 10);
            g2.drawLine(11, 6, 15, 10);
            g2.drawLine(11, 14, 15, 10);
        }

        private void paintRemove(Graphics2D g2) {
            g2.drawOval(3, 3, 14, 14);
            g2.drawLine(7, 7, 13, 13);
            g2.drawLine(13, 7, 7, 13);
        }

        private void paintClearAll(Graphics2D g2) {
            g2.drawLine(4, 6, 16, 6);
            g2.drawLine(6, 6, 7, 17);
            g2.drawLine(14, 6, 13, 17);
            g2.drawLine(8, 9, 8, 15);
            g2.drawLine(12, 9, 12, 15);
            g2.drawRoundRect(5, 4, 10, 2, 1, 1);
        }

        private void paintExport(Graphics2D g2) {
            g2.drawLine(10, 3, 10, 13);
            g2.drawLine(6, 9, 10, 13);
            g2.drawLine(14, 9, 10, 13);
            g2.drawLine(4, 16, 16, 16);
        }
    }
}
