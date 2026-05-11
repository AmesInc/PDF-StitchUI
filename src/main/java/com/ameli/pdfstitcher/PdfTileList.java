package com.ameli.pdfstitcher;

import javax.swing.JList;
import javax.swing.ListModel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.dnd.Autoscroll;

final class PdfTileList extends JList<PdfEntry> implements Autoscroll {
    private static final int AUTOSCROLL_MARGIN = 44;

    private int dropInsertionIndex = -1;

    PdfTileList(ListModel<PdfEntry> dataModel) {
        super(dataModel);
        setAutoscrolls(true);
    }

    void setDropInsertionIndex(int dropInsertionIndex) {
        this.dropInsertionIndex = dropInsertionIndex;
        repaint();
    }

    void clearDropInsertionIndex() {
        setDropInsertionIndex(-1);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        if (dropInsertionIndex < 0) {
            return;
        }

        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle markerBounds = resolveMarkerBounds();
        if (markerBounds != null) {
            g2.setColor(new Color(108, 161, 233, 90));
            g2.fillRoundRect(markerBounds.x - 4, markerBounds.y - 6, 8, markerBounds.height + 12, 8, 8);
            g2.setColor(new Color(47, 110, 196));
            g2.fillRoundRect(markerBounds.x - 2, markerBounds.y - 4, 4, markerBounds.height + 8, 4, 4);
        }

        g2.dispose();
    }

    @Override
    public Insets getAutoscrollInsets() {
        return new Insets(AUTOSCROLL_MARGIN, AUTOSCROLL_MARGIN, AUTOSCROLL_MARGIN, AUTOSCROLL_MARGIN);
    }

    @Override
    public void autoscroll(Point cursorLocation) {
        Rectangle visible = getVisibleRect();
        int scrollAmount = 36;

        if (cursorLocation.y < visible.y + AUTOSCROLL_MARGIN) {
            scrollRectToVisible(new Rectangle(visible.x, Math.max(0, visible.y - scrollAmount), visible.width, visible.height));
        } else if (cursorLocation.y > visible.y + visible.height - AUTOSCROLL_MARGIN) {
            scrollRectToVisible(new Rectangle(visible.x, visible.y + scrollAmount, visible.width, visible.height));
        }
    }

    private Rectangle resolveMarkerBounds() {
        int size = getModel().getSize();
        if (size == 0) {
            return new Rectangle(24, 24, 4, Math.max(80, getHeight() - 48));
        }

        if (dropInsertionIndex >= size) {
            Rectangle lastCell = getCellBounds(size - 1, size - 1);
            if (lastCell == null) {
                return null;
            }
            return new Rectangle(lastCell.x + lastCell.width - 8, lastCell.y + 12, 4, Math.max(40, lastCell.height - 24));
        }

        Rectangle targetCell = getCellBounds(dropInsertionIndex, dropInsertionIndex);
        if (targetCell == null) {
            return null;
        }
        return new Rectangle(targetCell.x + 8, targetCell.y + 12, 4, Math.max(40, targetCell.height - 24));
    }
}
