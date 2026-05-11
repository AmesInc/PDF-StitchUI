package com.ameli.pdfstitcher;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class PdfStitcherApp {
    private PdfStitcherApp() {
    }

    public static void main(String[] args) {
        configureLookAndFeel();
        SwingUtilities.invokeLater(() -> new PdfStitcherFrame().setVisible(true));
    }

    private static void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The app can run with the default look and feel if the system theme is unavailable.
        }
    }
}

