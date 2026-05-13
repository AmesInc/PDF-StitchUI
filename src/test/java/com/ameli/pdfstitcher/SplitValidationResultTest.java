package com.ameli.pdfstitcher;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitValidationResultTest {
    @Test
    void canExportRequiresValidStateAcrossGroups() {
        SplitGroup ready = new SplitGroup("File 1", "1-2");
        ready.setResolvedPages(List.of(0, 1));
        SplitValidationResult result = new SplitValidationResult(2, 2, List.of(), List.of(), List.of());

        assertTrue(result.canExport(List.of(ready), true));
        assertFalse(result.canExport(List.of(ready), false));

        ready.setValidationMessage("Problem");
        assertFalse(result.canExport(List.of(ready), true));

        ready.setValidationMessage(null);
        ready.setResolvedPages(List.of());
        assertFalse(result.canExport(List.of(ready), true));
    }

    @Test
    void canExportRejectsGeneralCoverageProblems() {
        SplitGroup ready = new SplitGroup("File 1", "1");
        ready.setResolvedPages(List.of(0));

        assertFalse(new SplitValidationResult(2, 1, List.of(1), List.of(), List.of()).canExport(List.of(ready), true));
        assertFalse(new SplitValidationResult(2, 2, List.of(), List.of(0), List.of()).canExport(List.of(ready), true));
        assertFalse(new SplitValidationResult(2, 2, List.of(), List.of(), List.of("Issue")).canExport(List.of(ready), true));
        assertFalse(new SplitValidationResult(0, 0, List.of(), List.of(), List.of()).canExport(List.of(ready), true));
    }
}
