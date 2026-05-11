package com.ameli.pdfstitcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

final class SplitAllocator {
    private static final String INVALID_FILE_CHARACTERS = "\\\\/:*?\"<>|";
    private static final String[] RESERVED_FILE_NAMES = {
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
    };

    private SplitAllocator() {
    }

    static SplitValidationResult validate(int totalPages, List<SplitGroup> groups) {
        for (SplitGroup group : groups) {
            group.setResolvedPages(List.of());
            group.setValidationMessage(null);
        }

        if (totalPages <= 0) {
            return new SplitValidationResult(0, 0, List.of(), List.of(), List.of("Choose a source PDF to allocate pages."));
        }

        Map<String, List<SplitGroup>> nameIndex = new LinkedHashMap<>();
        int[] usageCounts = new int[totalPages];
        List<String> generalIssues = new ArrayList<>();

        for (SplitGroup group : groups) {
            String outputName = group.getOutputName().trim();
            if (outputName.isBlank()) {
                appendIssue(group, "Output name is required.");
            } else if (!isValidFileName(outputName)) {
                appendIssue(group, "Output filename is invalid. Avoid \\\\ / : * ? \" < > | and reserved Windows names.");
            } else {
                nameIndex.computeIfAbsent(normalizeFileName(outputName), ignored -> new ArrayList<>()).add(group);
            }

            String pageSpec = group.getPageSpec().trim();
            if (pageSpec.isBlank()) {
                appendIssue(group, "Choose at least one page.");
                continue;
            }

            try {
                List<Integer> resolvedPages = PageRangeParser.parse(pageSpec, totalPages);
                if (resolvedPages.isEmpty()) {
                    appendIssue(group, "Choose at least one page.");
                    continue;
                }

                group.setResolvedPages(resolvedPages);
                for (Integer pageIndex : resolvedPages) {
                    usageCounts[pageIndex]++;
                }
            } catch (IllegalArgumentException exception) {
                appendIssue(group, exception.getMessage());
            }
        }

        for (Map.Entry<String, List<SplitGroup>> entry : nameIndex.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }

            for (SplitGroup group : entry.getValue()) {
                appendIssue(group, "Output filename duplicates another group.");
            }
        }

        List<Integer> duplicatePages = new ArrayList<>();
        List<Integer> unassignedPages = new ArrayList<>();
        int assignedUniquePages = 0;
        for (int pageIndex = 0; pageIndex < usageCounts.length; pageIndex++) {
            if (usageCounts[pageIndex] == 0) {
                unassignedPages.add(pageIndex);
            } else {
                assignedUniquePages++;
                if (usageCounts[pageIndex] > 1) {
                    duplicatePages.add(pageIndex);
                }
            }
        }

        if (!duplicatePages.isEmpty()) {
            for (SplitGroup group : groups) {
                List<Integer> groupDuplicates = new ArrayList<>();
                for (Integer pageIndex : group.getResolvedPages()) {
                    if (usageCounts[pageIndex] > 1) {
                        groupDuplicates.add(pageIndex);
                    }
                }
                if (!groupDuplicates.isEmpty()) {
                    appendIssue(group, "Contains duplicate pages: " + formatPageList(groupDuplicates));
                }
            }
        }

        if (groups.isEmpty()) {
            generalIssues.add("Add at least one output file.");
        }

        return new SplitValidationResult(totalPages, assignedUniquePages, unassignedPages, duplicatePages, generalIssues);
    }

    static String formatPageList(List<Integer> zeroBasedPages) {
        if (zeroBasedPages.isEmpty()) {
            return "none";
        }

        TreeSet<Integer> sortedPages = new TreeSet<>();
        for (Integer page : zeroBasedPages) {
            sortedPages.add(page + 1);
        }

        List<String> parts = new ArrayList<>();
        Integer rangeStart = null;
        Integer previous = null;
        for (Integer page : sortedPages) {
            if (rangeStart == null) {
                rangeStart = page;
                previous = page;
                continue;
            }

            if (page == previous + 1) {
                previous = page;
                continue;
            }

            parts.add(renderRange(rangeStart, previous));
            rangeStart = page;
            previous = page;
        }

        if (rangeStart != null) {
            parts.add(renderRange(rangeStart, previous));
        }

        return String.join(", ", parts);
    }

    private static String renderRange(int start, int end) {
        return start == end ? Integer.toString(start) : start + "-" + end;
    }

    private static String normalizeFileName(String fileName) {
        String normalized = fileName.trim().toLowerCase();
        return normalized.endsWith(".pdf") ? normalized : normalized + ".pdf";
    }

    private static boolean isValidFileName(String fileName) {
        String trimmed = fileName.trim();
        if (trimmed.isBlank() || trimmed.endsWith(" ") || trimmed.endsWith(".")) {
            return false;
        }

        for (int index = 0; index < INVALID_FILE_CHARACTERS.length(); index++) {
            if (trimmed.indexOf(INVALID_FILE_CHARACTERS.charAt(index)) >= 0) {
                return false;
            }
        }

        String baseName = trimmed;
        int extensionIndex = trimmed.lastIndexOf('.');
        if (extensionIndex > 0) {
            baseName = trimmed.substring(0, extensionIndex);
        }

        String normalizedBaseName = baseName.trim().toLowerCase();
        for (String reservedFileName : RESERVED_FILE_NAMES) {
            if (reservedFileName.equals(normalizedBaseName)) {
                return false;
            }
        }

        return true;
    }

    private static void appendIssue(SplitGroup group, String issue) {
        if (issue == null || issue.isBlank()) {
            return;
        }

        String existing = group.getValidationMessage();
        if (existing == null) {
            group.setValidationMessage(issue);
            return;
        }

        LinkedHashSet<String> messages = new LinkedHashSet<>();
        for (String part : existing.split("\n")) {
            if (!part.isBlank()) {
                messages.add(part.trim());
            }
        }
        messages.add(issue.trim());
        group.setValidationMessage(String.join("\n", messages));
    }
}
