package com.ameli.pdfstitcher;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

final class PdfTileRenderer extends JPanel implements ListCellRenderer<PdfEntry> {
    static final int CELL_WIDTH = 246;
    static final int CELL_HEIGHT = 354;
    static final int CARD_MARGIN = 10;
    static final int REMOVE_SIZE = 28;

    private final JLabel sequenceLabel = new JLabel();
    private final JLabel removeLabel = new JLabel("\u00d7", SwingConstants.CENTER);
    private final JLabel thumbnailLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel fileNameLabel = new JLabel();
    private final JLabel metadataLabel = new JLabel();
    private final JLabel configLabel = new JLabel();
    private final JLabel badgeLabel = new JLabel();

    private boolean selected;

    PdfTileRenderer() {
        setOpaque(false);
        setPreferredSize(new Dimension(CELL_WIDTH, CELL_HEIGHT));
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(CARD_MARGIN, CARD_MARGIN, CARD_MARGIN, CARD_MARGIN));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        sequenceLabel.setOpaque(true);
        sequenceLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        sequenceLabel.setFont(sequenceLabel.getFont().deriveFont(Font.BOLD, 12f));

        removeLabel.setOpaque(true);
        removeLabel.setPreferredSize(new Dimension(REMOVE_SIZE, REMOVE_SIZE));
        removeLabel.setMinimumSize(new Dimension(REMOVE_SIZE, REMOVE_SIZE));
        removeLabel.setMaximumSize(new Dimension(REMOVE_SIZE, REMOVE_SIZE));
        removeLabel.setFont(removeLabel.getFont().deriveFont(Font.BOLD, 16f));

        headerPanel.add(sequenceLabel, BorderLayout.WEST);
        headerPanel.add(removeLabel, BorderLayout.EAST);

        thumbnailLabel.setPreferredSize(new Dimension(180, 188));
        thumbnailLabel.setBorder(BorderFactory.createEmptyBorder(16, 12, 12, 12));
        thumbnailLabel.setVerticalAlignment(SwingConstants.CENTER);
        thumbnailLabel.setHorizontalAlignment(SwingConstants.CENTER);
        thumbnailLabel.setOpaque(true);
        thumbnailLabel.setBackground(new Color(249, 251, 255));

        fileNameLabel.setVerticalAlignment(SwingConstants.TOP);
        fileNameLabel.setFont(fileNameLabel.getFont().deriveFont(Font.BOLD, 13f));
        fileNameLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        metadataLabel.setFont(metadataLabel.getFont().deriveFont(12f));
        metadataLabel.setForeground(new Color(86, 94, 110));

        configLabel.setFont(configLabel.getFont().deriveFont(12f));
        configLabel.setForeground(new Color(72, 81, 95));

        badgeLabel.setFont(badgeLabel.getFont().deriveFont(Font.BOLD, 11f));
        badgeLabel.setOpaque(true);
        badgeLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JPanel footerPanel = new JPanel();
        footerPanel.setOpaque(false);
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));
        footerPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        fileNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        metadataLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        configLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        footerPanel.add(fileNameLabel);
        footerPanel.add(Box.createVerticalStrut(6));
        footerPanel.add(metadataLabel);
        footerPanel.add(Box.createVerticalStrut(4));
        footerPanel.add(configLabel);
        footerPanel.add(Box.createVerticalStrut(8));
        footerPanel.add(badgeLabel);

        add(headerPanel, BorderLayout.NORTH);
        add(thumbnailLabel, BorderLayout.CENTER);
        add(footerPanel, BorderLayout.SOUTH);
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends PdfEntry> list,
            PdfEntry value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
    ) {
        selected = isSelected;

        boolean hoveringRemove = index == readIndexProperty(list, "removeHoverIndex");
        boolean pressingRemove = index == readIndexProperty(list, "removePressedIndex");

        sequenceLabel.setText(String.format("Sequence %d", index + 1));
        sequenceLabel.setBackground(isSelected ? new Color(215, 233, 255) : new Color(239, 243, 250));
        sequenceLabel.setForeground(new Color(36, 58, 92));

        if (pressingRemove) {
            removeLabel.setBackground(new Color(221, 81, 81));
            removeLabel.setForeground(Color.WHITE);
        } else if (hoveringRemove) {
            removeLabel.setBackground(new Color(255, 219, 219));
            removeLabel.setForeground(new Color(140, 24, 24));
        } else {
            removeLabel.setBackground(isSelected ? new Color(255, 227, 227) : new Color(246, 247, 250));
            removeLabel.setForeground(new Color(140, 24, 24));
        }

        Icon thumbnail = value.getThumbnail();
        if (thumbnail != null) {
            thumbnailLabel.setIcon(thumbnail);
            thumbnailLabel.setText("");
        } else {
            thumbnailLabel.setIcon(null);
            thumbnailLabel.setText(value.getThumbnailMessage() == null ? "No preview" : value.getThumbnailMessage());
            thumbnailLabel.setForeground(new Color(102, 110, 125));
        }

        fileNameLabel.setText(toHtml(value.getDisplayName()));
        metadataLabel.setText(value.getMetadataSummary());
        configLabel.setText(value.getConfigSummary());

        if (value.hasBlockingIssue()) {
            badgeLabel.setText(value.getBlockingIssue());
            badgeLabel.setForeground(new Color(132, 27, 27));
            badgeLabel.setBackground(new Color(255, 232, 232));
        } else if (value.hasAdvisory()) {
            badgeLabel.setText(value.getAdvisoryMessage());
            badgeLabel.setForeground(new Color(130, 66, 7));
            badgeLabel.setBackground(new Color(255, 240, 214));
        } else {
            badgeLabel.setText("Ready to export");
            badgeLabel.setForeground(new Color(34, 95, 54));
            badgeLabel.setBackground(new Color(223, 244, 231));
        }

        return this;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Insets insets = getInsets();
        int x = insets.left;
        int y = insets.top;
        int width = getWidth() - insets.left - insets.right - 1;
        int height = getHeight() - insets.top - insets.bottom - 1;

        g2.setColor(selected ? new Color(233, 242, 255) : Color.WHITE);
        g2.fillRoundRect(x, y, width, height, 22, 22);

        g2.setColor(selected ? new Color(110, 158, 220) : new Color(210, 219, 232));
        g2.drawRoundRect(x, y, width, height, 22, 22);

        g2.dispose();
        super.paintComponent(graphics);
    }

    static int removeButtonLeft() {
        return CELL_WIDTH - CARD_MARGIN - REMOVE_SIZE - 8;
    }

    static int removeButtonTop() {
        return CARD_MARGIN + 6;
    }

    private int readIndexProperty(JList<? extends PdfEntry> list, String propertyName) {
        Object property = list.getClientProperty(propertyName);
        return property instanceof Integer value ? value : -1;
    }

    private String toHtml(String text) {
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return "<html><div style='width:182px;'>" + escaped + "</div></html>";
    }
}
