package com.ameli.pdfstitcher;

import javax.swing.ImageIcon;
import java.nio.file.Path;

final class PdfEntry {
    private final Path path;
    private ImageIcon thumbnail;
    private boolean loadingThumbnail = true;
    private String thumbnailMessage = "Loading preview...";

    PdfEntry(Path path) {
        this.path = path;
    }

    Path getPath() {
        return path;
    }

    String getDisplayName() {
        return path.getFileName().toString();
    }

    ImageIcon getThumbnail() {
        return thumbnail;
    }

    void setThumbnail(ImageIcon thumbnail) {
        this.thumbnail = thumbnail;
        this.loadingThumbnail = false;
        this.thumbnailMessage = null;
    }

    boolean isLoadingThumbnail() {
        return loadingThumbnail;
    }

    String getThumbnailMessage() {
        return thumbnailMessage;
    }

    void setThumbnailError(String message) {
        this.thumbnail = null;
        this.loadingThumbnail = false;
        this.thumbnailMessage = message;
    }
}

