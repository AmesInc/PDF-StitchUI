package com.ameli.pdfstitcher;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class SplitExporter {
    private SplitExporter() {
    }

    static List<Path> export(Path sourcePath, List<SplitGroup> groups, Path outputDirectory) throws IOException {
        List<Path> exportedFiles = new ArrayList<>();
        Files.createDirectories(outputDirectory);

        try (PDDocument sourceDocument = Loader.loadPDF(sourcePath.toFile())) {
            for (SplitGroup group : groups) {
                Path targetPath = outputDirectory.resolve(ensurePdfExtension(group.getOutputName().trim()));
                Path tempFile = Files.createTempFile(outputDirectory, "pdf-stitchui-split-", ".pdf");

                try (PDDocument outputDocument = new PDDocument()) {
                    for (Integer pageIndex : group.getResolvedPages()) {
                        PDPage importedPage = outputDocument.importPage(sourceDocument.getPage(pageIndex));
                        importedPage.setRotation(sourceDocument.getPage(pageIndex).getRotation());
                    }
                    outputDocument.save(tempFile.toFile());
                } catch (IOException exception) {
                    Files.deleteIfExists(tempFile);
                    throw new IOException("Failed while writing '" + targetPath.getFileName() + "' after " + exportedFiles.size() + " file(s) succeeded.", exception);
                }

                try {
                    Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException exception) {
                    Files.deleteIfExists(tempFile);
                    throw new IOException("Failed while finalizing '" + targetPath.getFileName() + "' after " + exportedFiles.size() + " file(s) succeeded.", exception);
                }

                exportedFiles.add(targetPath);
            }
        }

        return List.copyOf(exportedFiles);
    }

    static String ensurePdfExtension(String fileName) {
        return fileName.toLowerCase().endsWith(".pdf") ? fileName : fileName + ".pdf";
    }
}
