package com.ameli.pdfstitcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class PageRangeParser {
    private PageRangeParser() {
    }

    static List<Integer> parse(String specification, int totalPages) {
        if (totalPages <= 0) {
            return List.of();
        }

        String spec = specification == null ? "" : specification.trim();
        if (spec.isBlank()) {
            List<Integer> allPages = new ArrayList<>(totalPages);
            for (int index = 0; index < totalPages; index++) {
                allPages.add(index);
            }
            return List.copyOf(allPages);
        }

        LinkedHashSet<Integer> pages = new LinkedHashSet<>();
        String[] segments = spec.split(",");
        for (String rawSegment : segments) {
            String segment = rawSegment.trim();
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("Page ranges cannot contain empty segments.");
            }

            if (segment.contains("-")) {
                String[] bounds = segment.split("-", -1);
                if (bounds.length != 2 || bounds[0].isBlank() || bounds[1].isBlank()) {
                    throw new IllegalArgumentException("Invalid range '" + segment + "'. Use values like 2-5.");
                }

                int start = parsePageNumber(bounds[0], totalPages);
                int end = parsePageNumber(bounds[1], totalPages);
                if (start > end) {
                    throw new IllegalArgumentException("Range '" + segment + "' counts backward.");
                }

                for (int page = start; page <= end; page++) {
                    pages.add(page - 1);
                }
            } else {
                pages.add(parsePageNumber(segment, totalPages) - 1);
            }
        }

        return List.copyOf(pages);
    }

    private static int parsePageNumber(String value, int totalPages) {
        try {
            int pageNumber = Integer.parseInt(value.trim());
            if (pageNumber < 1 || pageNumber > totalPages) {
                throw new IllegalArgumentException("Page " + pageNumber + " is outside 1-" + totalPages + ".");
            }
            return pageNumber;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid page number '" + value.trim() + "'.");
        }
    }
}
