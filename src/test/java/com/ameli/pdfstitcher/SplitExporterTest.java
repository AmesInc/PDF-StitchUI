package com.ameli.pdfstitcher;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void exportWritesExpectedFilesAndPreservesRotation() throws IOException {
        Path source = createSourcePdf(tempDir.resolve("source.pdf"));
        Path outputDirectory = tempDir.resolve("output");

        SplitGroup first = new SplitGroup("Alpha", "1,3");
        first.setResolvedPages(List.of(0, 2));
        SplitGroup second = new SplitGroup("Beta.pdf", "2");
        second.setResolvedPages(List.of(1));

        List<Path> exported = SplitExporter.export(source, List.of(first, second), outputDirectory);

        assertEquals(List.of(outputDirectory.resolve("Alpha.pdf"), outputDirectory.resolve("Beta.pdf")), exported);
        assertTrue(Files.exists(exported.get(0)));
        assertTrue(Files.exists(exported.get(1)));

        try (PDDocument alpha = Loader.loadPDF(exported.get(0).toFile());
             PDDocument beta = Loader.loadPDF(exported.get(1).toFile())) {
            assertEquals(2, alpha.getNumberOfPages());
            assertEquals(1, beta.getNumberOfPages());
            assertEquals(0, alpha.getPage(0).getRotation());
            assertEquals(180, alpha.getPage(1).getRotation());
            assertEquals(90, beta.getPage(0).getRotation());
        }
    }

    @Test
    void exportOverwritesExistingTargets() throws IOException {
        Path source = createSourcePdf(tempDir.resolve("source.pdf"));
        Path outputDirectory = tempDir.resolve("output");
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("Alpha.pdf"), "old");

        SplitGroup group = new SplitGroup("Alpha", "1");
        group.setResolvedPages(List.of(0));

        List<Path> exported = SplitExporter.export(source, List.of(group), outputDirectory);

        try (PDDocument alpha = Loader.loadPDF(exported.getFirst().toFile())) {
            assertEquals(1, alpha.getNumberOfPages());
        }
    }

    @Test
    void ensurePdfExtensionOnlyAddsMissingSuffix() {
        assertEquals("report.pdf", SplitExporter.ensurePdfExtension("report"));
        assertEquals("report.PDF", SplitExporter.ensurePdfExtension("report.PDF"));
    }

    private Path createSourcePdf(Path path) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage();
            PDPage second = new PDPage();
            second.setRotation(90);
            PDPage third = new PDPage();
            third.setRotation(180);

            document.addPage(first);
            document.addPage(second);
            document.addPage(third);
            document.save(path.toFile());
        }
        return path;
    }
}
