package com.ameli.pdfstitcher;

import java.util.List;

final class SplitValidationResult {
    private final int totalPages;
    private final int assignedUniquePages;
    private final List<Integer> unassignedPages;
    private final List<Integer> duplicatePages;
    private final List<String> generalIssues;

    SplitValidationResult(
            int totalPages,
            int assignedUniquePages,
            List<Integer> unassignedPages,
            List<Integer> duplicatePages,
            List<String> generalIssues
    ) {
        this.totalPages = totalPages;
        this.assignedUniquePages = assignedUniquePages;
        this.unassignedPages = List.copyOf(unassignedPages);
        this.duplicatePages = List.copyOf(duplicatePages);
        this.generalIssues = List.copyOf(generalIssues);
    }

    int getTotalPages() {
        return totalPages;
    }

    int getAssignedUniquePages() {
        return assignedUniquePages;
    }

    List<Integer> getUnassignedPages() {
        return unassignedPages;
    }

    List<Integer> getDuplicatePages() {
        return duplicatePages;
    }

    List<String> getGeneralIssues() {
        return generalIssues;
    }

    boolean canExport(List<SplitGroup> groups, boolean hasSource) {
        if (!hasSource || totalPages <= 0 || groups.isEmpty()) {
            return false;
        }

        if (!generalIssues.isEmpty() || !unassignedPages.isEmpty() || !duplicatePages.isEmpty()) {
            return false;
        }

        for (SplitGroup group : groups) {
            if (group.hasValidationIssue() || group.getResolvedPages().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
