package com.ameli.pdfstitcher;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageRangeParserTest {
    @Test
    void returnsEveryPageForBlankSpecification() {
        assertEquals(List.of(0, 1, 2, 3), PageRangeParser.parse("   ", 4));
    }

    @Test
    void preservesExplicitOrderWhileRemovingDuplicates() {
        assertEquals(List.of(2, 0, 1, 3), PageRangeParser.parse("3,1-2,2,4", 4));
    }

    @Test
    void returnsEmptyWhenNoPagesExist() {
        assertEquals(List.of(), PageRangeParser.parse("1-3", 0));
    }

    @Test
    void rejectsUnexpectedCharacters() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PageRangeParser.parse("file.pdf", 5)
        );

        assertEquals("Page selection accepts only numbers, commas, spaces, and hyphens.", exception.getMessage());
    }

    @Test
    void rejectsEmptySegments() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PageRangeParser.parse("1,,3", 5)
        );

        assertEquals("Page ranges cannot contain empty segments.", exception.getMessage());
    }

    @Test
    void rejectsBackwardRanges() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PageRangeParser.parse("4-2", 5)
        );

        assertEquals("Range '4-2' counts backward.", exception.getMessage());
    }

    @Test
    void rejectsOutOfRangePages() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PageRangeParser.parse("6", 5)
        );

        assertEquals("Page 6 is outside 1-5.", exception.getMessage());
    }

    @Test
    void rejectsMalformedRange() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PageRangeParser.parse("2-", 5)
        );

        assertEquals("Invalid range '2-'. Use values like 2-5.", exception.getMessage());
    }
}
