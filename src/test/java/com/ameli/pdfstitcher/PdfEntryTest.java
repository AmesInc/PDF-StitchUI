package com.ameli.pdfstitcher;

import org.junit.jupiter.api.Test;

import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfEntryTest {
    @Test
    void loadSuccessSelectsAllPagesByDefault() {
        PdfEntry entry = new PdfEntry(Path.of("sample.pdf"));

        entry.applyLoadSuccess(new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)), 3, 2_048, false);

        assertEquals(List.of(0, 1, 2), entry.getSelectedPages());
        assertTrue(entry.canExport());
        assertEquals("3 of 3 pages  •  2 KB", entry.getMetadataSummary());
        assertEquals("All pages  •  No rotation", entry.getConfigSummary());
    }

    @Test
    void pageRangeValidationBlocksExport() {
        PdfEntry entry = new PdfEntry(Path.of("sample.pdf"));
        entry.applyLoadSuccess(null, 4, 512, true);

        entry.setPageRangeSpec("report.pdf");

        assertEquals(List.of(), entry.getSelectedPages());
        assertTrue(entry.hasBlockingIssue());
        assertEquals("Page selection accepts only numbers, commas, spaces, and hyphens.", entry.getBlockingIssue());
        assertFalse(entry.canExport());
        assertTrue(entry.hasAdvisory());
        assertEquals("Encrypted PDF loaded without a password prompt.", entry.getAdvisoryMessage());
    }

    @Test
    void rotationWrapsInBothDirections() {
        PdfEntry entry = new PdfEntry(Path.of("sample.pdf"));

        entry.rotateLeft();
        assertEquals(270, entry.getRotationDegrees());

        entry.rotateRight();
        entry.rotateRight();
        assertEquals(90, entry.getRotationDegrees());
    }

    @Test
    void loadFailureClearsExportability() {
        PdfEntry entry = new PdfEntry(Path.of("broken.pdf"));

        entry.applyLoadFailure("Could not read PDF.", 99);

        assertEquals("Could not read PDF.", entry.getBlockingIssue());
        assertEquals("Preview unavailable", entry.getThumbnailMessage());
        assertFalse(entry.canExport());
        assertFalse(entry.hasAdvisory());
        assertEquals("Page count unavailable  •  99 B", entry.getMetadataSummary());
    }

    @Test
    void copyProducesIndependentSelectionState() {
        PdfEntry entry = new PdfEntry(Path.of("sample.pdf"));
        entry.applyLoadSuccess(null, 5, 1_536, false);
        entry.setPageRangeSpec("1,3-4");
        entry.rotateRight();

        PdfEntry copy = entry.copy();
        copy.setPageRangeSpec("2");
        copy.rotateRight();

        assertEquals(List.of(0, 2, 3), entry.getSelectedPages());
        assertEquals(List.of(1), copy.getSelectedPages());
        assertEquals(90, entry.getRotationDegrees());
        assertEquals(180, copy.getRotationDegrees());
        assertNotSame(entry.getSelectedPages(), copy.getSelectedPages());
    }

    @Test
    void loadingStateHasFriendlySummaries() {
        PdfEntry entry = new PdfEntry(Path.of("sample.pdf"));

        assertTrue(entry.isLoading());
        assertEquals("Reading PDF metadata...", entry.getMetadataSummary());
        assertEquals("Preparing tile settings...", entry.getConfigSummary());
        assertNull(entry.getThumbnail());
    }
}
