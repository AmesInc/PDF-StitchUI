package com.ameli.pdfstitcher;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitAllocatorTest {
    @Test
    void validateFlagsMissingSource() {
        SplitValidationResult result = SplitAllocator.validate(0, List.of(new SplitGroup("File 1", "1")));

        assertEquals(List.of("Choose a source PDF to allocate pages."), result.getGeneralIssues());
        assertFalse(result.canExport(List.of(new SplitGroup("File 1", "1")), false));
    }

    @Test
    void validateDetectsFilenameProblemsDuplicatesAndUnassignedPages() {
        SplitGroup invalidName = new SplitGroup("con", "1");
        SplitGroup duplicateA = new SplitGroup("report", "2,4");
        SplitGroup duplicateB = new SplitGroup("report.pdf", "4");

        SplitValidationResult result = SplitAllocator.validate(5, List.of(invalidName, duplicateA, duplicateB));

        assertEquals(List.of(2, 4), result.getUnassignedPages());
        assertEquals(List.of(3), result.getDuplicatePages());
        assertEquals("Output filename is invalid. Avoid \\\\ / : * ? \" < > | and reserved Windows names.", invalidName.getValidationMessage());
        assertTrue(duplicateA.getValidationMessage().contains("Output filename duplicates another group."));
        assertTrue(duplicateA.getValidationMessage().contains("Contains duplicate pages: 4"));
        assertTrue(duplicateB.getValidationMessage().contains("Output filename duplicates another group."));
        assertTrue(duplicateB.getValidationMessage().contains("Contains duplicate pages: 4"));
        assertEquals(List.of(1, 3), duplicateA.getResolvedPages());
    }

    @Test
    void validateRequiresAtLeastOneGroup() {
        SplitValidationResult result = SplitAllocator.validate(4, List.of());

        assertEquals(List.of("Add at least one output file."), result.getGeneralIssues());
        assertFalse(result.canExport(List.of(), true));
    }

    @Test
    void validateMarksBadPageSpecs() {
        SplitGroup group = new SplitGroup("File 1", "not-pages");

        SplitValidationResult result = SplitAllocator.validate(4, List.of(group));

        assertEquals(List.of(0, 1, 2, 3), result.getUnassignedPages());
        assertEquals("Page selection accepts only numbers, commas, spaces, and hyphens.", group.getValidationMessage());
        assertEquals(List.of(), group.getResolvedPages());
    }

    @Test
    void formatPageListCompressesSequentialRanges() {
        assertEquals("1-3, 5, 7-8", SplitAllocator.formatPageList(List.of(0, 1, 2, 4, 6, 7)));
        assertEquals("none", SplitAllocator.formatPageList(List.of()));
    }
}
