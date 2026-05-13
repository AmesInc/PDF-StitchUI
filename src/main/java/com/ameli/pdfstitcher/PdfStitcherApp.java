package com.ameli.pdfstitcher;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class PdfStitcherApp {
    private static final int SINGLE_INSTANCE_PORT = 47631;

    private PdfStitcherApp() {
    }

    public static void main(String[] args) {
        AppDiagnostics.initialize();
        SingleInstanceCoordinator coordinator = SingleInstanceCoordinator.createOrSignalExisting();
        if (coordinator == null) {
            return;
        }

        configureLookAndFeel();
        AppDiagnostics.info("Launching PDF-StitchUI.");
        SwingUtilities.invokeLater(() -> {
            PdfStitcherFrame frame = new PdfStitcherFrame();
            coordinator.setActivationHandler(frame::activateWindow);
            frame.setVisible(true);
            frame.activateWindow();
            frame.restoreSessionAsync();
        });
    }

    private static void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The app can run with the default look and feel if the system theme is unavailable.
        }
    }

    private static final class SingleInstanceCoordinator {
        private final ServerSocket serverSocket;
        private Runnable activationHandler = () -> { };

        private SingleInstanceCoordinator(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
            startListener();
        }

        static SingleInstanceCoordinator createOrSignalExisting() {
            try {
                return new SingleInstanceCoordinator(new ServerSocket(SINGLE_INSTANCE_PORT, 50));
            } catch (IOException alreadyRunning) {
                notifyExistingInstance();
                return null;
            }
        }

        void setActivationHandler(Runnable activationHandler) {
            this.activationHandler = activationHandler;
        }

        private void startListener() {
            Thread listener = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try (Socket ignored = serverSocket.accept()) {
                        SwingUtilities.invokeLater(activationHandler);
                    } catch (IOException ignored) {
                        break;
                    }
                }
            }, "pdf-stitchui-single-instance-listener");
            listener.setDaemon(true);
            listener.start();
        }

        private static void notifyExistingInstance() {
            try (Socket socket = new Socket("127.0.0.1", SINGLE_INSTANCE_PORT);
                 PrintWriter ignored = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                // Connecting is enough to signal the running instance.
            } catch (IOException ignored) {
                // If focusing fails, avoiding duplicate windows is still preferable.
            }
        }
    }
}
