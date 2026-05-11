package com.ameli.pdfstitcher;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.io.MemoryUsageSetting;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class PdfStitcherFrame extends JFrame {
    private final DefaultListModel<PdfEntry> listModel = new DefaultListModel<>();
    private final JList<PdfEntry> pdfList = new JList<>(listModel);
    private final JPanel centerPanel = new JPanel(new BorderLayout());
    private final JScrollPane listScrollPane = new JScrollPane(pdfList);
    private final JLabel emptyStateLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("Add PDF files to start your sequence.");
    private final JButton addButton = new JButton("Add PDFs");
    private final JButton exportButton = new JButton("Export Stitched PDF");
    private final TransferHandler fileImportHandler = new FileImportTransferHandler();
    private Path lastDirectory;

    PdfStitcherFrame() {
        super("PDF Tile Stitcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 760));
        setPreferredSize(new Dimension(1240, 860));

        buildFrame();
        registerInteractions();
        refreshState();

        pack();
        setLocationRelativeTo(null);
    }

    private void buildFrame() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBorder(new EmptyBorder(18, 22, 22, 22));
        root.setBackground(new Color(246, 249, 253));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildCenterPanel(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JComponent buildHeader() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("PDF Stitcher");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));

        JLabel subtitle = new JLabel("Arrange PDF tiles in the order you want, then export one stitched file.");
        subtitle.setForeground(new Color(84, 96, 115));
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 14, 0));

        JToolBar toolbar = new JToolBar();
        toolbar.setOpaque(false);
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder());

        addButton.setFocusable(false);
        exportButton.setFocusable(false);

        toolbar.add(addButton);
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(exportButton);

        wrapper.add(title);
        wrapper.add(subtitle);
        wrapper.add(toolbar);

        return wrapper;
    }

    private JComponent buildCenterPanel() {
        pdfList.setCellRenderer(new PdfTileRenderer());
        pdfList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        pdfList.setVisibleRowCount(-1);
        pdfList.setFixedCellWidth(PdfTileRenderer.CELL_WIDTH);
        pdfList.setFixedCellHeight(PdfTileRenderer.CELL_HEIGHT);
        pdfList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
        emptyStateLabel.setText(
                "<html><div style='text-align:center;'>No PDFs yet.<br/>Use <b>Add PDFs</b> or drag PDF files into this window.</div></html>"
        );

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
        exportButton.addActionListener(event -> exportMergedPdf());

        pdfList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int index = pdfList.locationToIndex(event.getPoint());
                if (index < 0) {
                    return;
                }

                Rectangle bounds = pdfList.getCellBounds(index, index);
                if (bounds != null && removeButtonBounds(bounds).contains(event.getPoint())) {
                    removeAt(index);
                }
            }
        });

        pdfList.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                int index = pdfList.locationToIndex(event.getPoint());
                if (index < 0) {
                    pdfList.setCursor(Cursor.getDefaultCursor());
                    return;
                }

                Rectangle bounds = pdfList.getCellBounds(index, index);
                boolean overRemove = bounds != null && removeButtonBounds(bounds).contains(event.getPoint());
                pdfList.setCursor(overRemove ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        });

        pdfList.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "removeSelectedPdf");
        pdfList.getActionMap().put("removeSelectedPdf", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                int index = pdfList.getSelectedIndex();
                if (index >= 0) {
                    removeAt(index);
                }
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
            if (file != null && isPdf(file.toPath())) {
                paths.add(file.toPath());
            }
        }

        if (paths.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Only PDF files can be added.", "Unsupported files", JOptionPane.WARNING_MESSAGE);
            return;
        }

        lastDirectory = paths.getFirst().getParent();
        addPdfFiles(paths, listModel.getSize());
    }

    private void addPdfFiles(List<Path> paths, int insertIndex) {
        int position = Math.max(0, Math.min(insertIndex, listModel.getSize()));
        for (Path path : paths) {
            PdfEntry entry = new PdfEntry(path.toAbsolutePath());
            listModel.add(position++, entry);
            loadThumbnail(entry);
        }

        refreshState();
        statusLabel.setText(paths.size() == 1 ? "Added 1 PDF." : "Added " + paths.size() + " PDFs.");
    }

    private void removeAt(int index) {
        if (index < 0 || index >= listModel.size()) {
            return;
        }

        String removedName = listModel.get(index).getDisplayName();
        listModel.remove(index);
        refreshState();
        statusLabel.setText("Removed " + removedName + ".");
    }

    private void exportMergedPdf() {
        if (listModel.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        FileDialog dialog = new FileDialog(this, "Export Stitched PDF", FileDialog.SAVE);
        dialog.setFile("stitched.pdf");
        if (lastDirectory != null) {
            dialog.setDirectory(lastDirectory.toString());
        }

        dialog.setVisible(true);
        if (dialog.getFile() == null) {
            return;
        }

        Path destination = Path.of(dialog.getDirectory(), ensurePdfExtension(dialog.getFile()));
        lastDirectory = destination.getParent();

        if (Files.exists(destination)) {
            int overwrite = JOptionPane.showConfirmDialog(
                    this,
                    "Replace the existing file?\n" + destination,
                    "Confirm overwrite",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }

        setBusy(true, "Exporting stitched PDF...");

        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                PDFMergerUtility merger = new PDFMergerUtility();
                for (int index = 0; index < listModel.size(); index++) {
                    merger.addSource(listModel.get(index).getPath().toFile());
                }

                merger.setDestinationFileName(destination.toString());
                merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
                return destination;
            }

            @Override
            protected void done() {
                try {
                    Path exported = get();
                    setBusy(false, "Exported stitched PDF to " + exported + ".");
                    JOptionPane.showMessageDialog(
                            PdfStitcherFrame.this,
                            "Created stitched PDF:\n" + exported,
                            "Export complete",
                            JOptionPane.INFORMATION_MESSAGE
                    );
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

    private void loadThumbnail(PdfEntry entry) {
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                try (PDDocument document = PDDocument.load(entry.getPath().toFile())) {
                    if (document.getNumberOfPages() == 0) {
                        throw new IOException("The PDF has no pages.");
                    }

                    PDFRenderer renderer = new PDFRenderer(document);
                    return renderer.renderImageWithDPI(0, 120);
                }
            }

            @Override
            protected void done() {
                try {
                    BufferedImage image = get();
                    entry.setThumbnail(new javax.swing.ImageIcon(scaleToTile(image)));
                } catch (Exception exception) {
                    entry.setThumbnailError("Preview unavailable");
                }

                pdfList.repaint();
            }
        }.execute();
    }

    private Image scaleToTile(BufferedImage image) {
        int maxWidth = 170;
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

        exportButton.setEnabled(!listModel.isEmpty());
        centerPanel.revalidate();
        centerPanel.repaint();
    }

    private void setBusy(boolean busy, String message) {
        addButton.setEnabled(!busy);
        exportButton.setEnabled(!busy && !listModel.isEmpty());
        pdfList.setEnabled(!busy);
        statusLabel.setText(message);
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    private Rectangle removeButtonBounds(Rectangle cellBounds) {
        return new Rectangle(
                cellBounds.x + PdfTileRenderer.removeButtonLeft(),
                cellBounds.y + PdfTileRenderer.removeButtonTop(),
                PdfTileRenderer.REMOVE_SIZE,
                PdfTileRenderer.REMOVE_SIZE
        );
    }

    private boolean isPdf(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".pdf");
    }

    private String ensurePdfExtension(String fileName) {
        return fileName.toLowerCase().endsWith(".pdf") ? fileName : fileName + ".pdf";
    }

    private final class PdfListTransferHandler extends TransferHandler {
        private final DataFlavor localEntryFlavor;

        PdfListTransferHandler() {
            try {
                localEntryFlavor = new DataFlavor(
                        DataFlavor.javaJVMLocalObjectMimeType + ";class=" + DraggedEntry.class.getName()
                );
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
            PdfEntry selected = pdfList.getSelectedValue();
            int sourceIndex = pdfList.getSelectedIndex();
            if (selected == null || sourceIndex < 0) {
                return null;
            }

            return new SimpleTransferable(new DraggedEntry(selected, sourceIndex), localEntryFlavor);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                support.setShowDropLocation(true);
                return true;
            }

            boolean supportsReorder = support.isDrop() && support.isDataFlavorSupported(localEntryFlavor);
            if (supportsReorder) {
                support.setShowDropLocation(true);
            }
            return supportsReorder;
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    @SuppressWarnings("unchecked")
                    List<File> droppedFiles = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    List<Path> pdfPaths = droppedFiles.stream()
                            .map(File::toPath)
                            .filter(PdfStitcherFrame.this::isPdf)
                            .toList();

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

                    listModel.remove(draggedEntry.sourceIndex());
                    listModel.add(targetIndex, draggedEntry.entry());
                    pdfList.setSelectedIndex(targetIndex);
                    statusLabel.setText("Moved " + draggedEntry.entry().getDisplayName() + " to sequence " + (targetIndex + 1) + ".");
                    refreshState();
                    return true;
                }
            } catch (UnsupportedFlavorException | IOException exception) {
                UIManager.getLookAndFeel().provideErrorFeedback(PdfStitcherFrame.this);
            }

            return false;
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
                List<Path> pdfPaths = droppedFiles.stream()
                        .map(File::toPath)
                        .filter(PdfStitcherFrame.this::isPdf)
                        .toList();

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
}
