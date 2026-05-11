package com.ameli.pdfstitcher;

import javax.swing.ImageIcon;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class PdfEntry {
    private final Path path;
    private ImageIcon thumbnail;
    private boolean loading = true;
    private String thumbnailMessage = "Loading preview...";
    private long fileSize = -1L;
    private int totalPages = -1;
    private List<Integer> selectedPages = List.of();
    private String pageRangeSpec = "";
    private int rotationDegrees;
    private String loadError;
    private String advisoryMessage;
    private String validationMessage;

    PdfEntry(Path path) {
        this.path = path.toAbsolutePath();
    }

    PdfEntry copy() {
        PdfEntry copy = new PdfEntry(path);
        copy.thumbnail = thumbnail;
        copy.loading = loading;
        copy.thumbnailMessage = thumbnailMessage;
        copy.fileSize = fileSize;
        copy.totalPages = totalPages;
        copy.selectedPages = new ArrayList<>(selectedPages);
        copy.pageRangeSpec = pageRangeSpec;
        copy.rotationDegrees = rotationDegrees;
        copy.loadError = loadError;
        copy.advisoryMessage = advisoryMessage;
        copy.validationMessage = validationMessage;
        return copy;
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

    boolean isLoading() {
        return loading;
    }

    String getThumbnailMessage() {
        return thumbnailMessage;
    }

    long getFileSize() {
        return fileSize;
    }

    int getTotalPages() {
        return totalPages;
    }

    List<Integer> getSelectedPages() {
        return selectedPages;
    }

    String getPageRangeSpec() {
        return pageRangeSpec;
    }

    int getRotationDegrees() {
        return rotationDegrees;
    }

    void rotateLeft() {
        rotationDegrees = Math.floorMod(rotationDegrees - 90, 360);
    }

    void rotateRight() {
        rotationDegrees = Math.floorMod(rotationDegrees + 90, 360);
    }

    void setPageRangeSpec(String pageRangeSpec) {
        this.pageRangeSpec = pageRangeSpec == null ? "" : pageRangeSpec.trim();
        recomputeSelectedPages();
    }

    boolean canExport() {
        return !loading && loadError == null && validationMessage == null && !selectedPages.isEmpty();
    }

    boolean hasBlockingIssue() {
        return loadError != null || validationMessage != null;
    }

    String getBlockingIssue() {
        return loadError != null ? loadError : validationMessage;
    }

    boolean hasAdvisory() {
        return advisoryMessage != null && !advisoryMessage.isBlank();
    }

    String getAdvisoryMessage() {
        return advisoryMessage;
    }

    String getMetadataSummary() {
        if (loading) {
            return "Reading PDF metadata...";
        }

        String pageSummary;
        if (totalPages > 0) {
            pageSummary = selectedPages.size() + " of " + totalPages + (totalPages == 1 ? " page" : " pages");
        } else {
            pageSummary = "Page count unavailable";
        }

        String sizeSummary = fileSize >= 0 ? formatFileSize(fileSize) : "Unknown size";
        return pageSummary + "  •  " + sizeSummary;
    }

    String getConfigSummary() {
        if (loading) {
            return "Preparing tile settings...";
        }

        String rangeSummary = pageRangeSpec.isBlank() ? "All pages" : "Pages " + pageRangeSpec;
        String rotationSummary = rotationDegrees == 0 ? "No rotation" : "Rotate " + rotationDegrees + "\u00b0";
        return rangeSummary + "  •  " + rotationSummary;
    }

    void applyLoadSuccess(ImageIcon thumbnail, int totalPages, long fileSize, boolean encrypted) {
        this.thumbnail = thumbnail;
        this.totalPages = totalPages;
        this.fileSize = fileSize;
        this.loading = false;
        this.thumbnailMessage = null;
        this.loadError = null;
        this.advisoryMessage = encrypted ? "Encrypted PDF loaded without a password prompt." : null;
        recomputeSelectedPages();
    }

    void applyLoadFailure(String loadError, long fileSize) {
        this.thumbnail = null;
        this.loading = false;
        this.fileSize = fileSize;
        this.totalPages = -1;
        this.selectedPages = List.of();
        this.thumbnailMessage = "Preview unavailable";
        this.loadError = loadError;
        this.advisoryMessage = null;
        this.validationMessage = null;
    }

    private void recomputeSelectedPages() {
        if (totalPages <= 0) {
            selectedPages = List.of();
            validationMessage = null;
            return;
        }

        try {
            selectedPages = PageRangeParser.parse(pageRangeSpec, totalPages);
            validationMessage = selectedPages.isEmpty() ? "The selected page range is empty." : null;
        } catch (IllegalArgumentException exception) {
            selectedPages = List.of();
            validationMessage = exception.getMessage();
        }
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
}
