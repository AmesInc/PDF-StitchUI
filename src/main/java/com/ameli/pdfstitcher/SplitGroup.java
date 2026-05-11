package com.ameli.pdfstitcher;

import java.util.List;

final class SplitGroup {
    private String outputName;
    private String pageSpec;
    private List<Integer> resolvedPages = List.of();
    private String validationMessage;

    SplitGroup(String outputName, String pageSpec) {
        this.outputName = outputName == null ? "" : outputName.trim();
        this.pageSpec = pageSpec == null ? "" : pageSpec.trim();
    }

    String getOutputName() {
        return outputName;
    }

    void setOutputName(String outputName) {
        this.outputName = outputName == null ? "" : outputName.trim();
    }

    String getPageSpec() {
        return pageSpec;
    }

    void setPageSpec(String pageSpec) {
        this.pageSpec = pageSpec == null ? "" : pageSpec.trim();
    }

    List<Integer> getResolvedPages() {
        return resolvedPages;
    }

    void setResolvedPages(List<Integer> resolvedPages) {
        this.resolvedPages = resolvedPages == null ? List.of() : List.copyOf(resolvedPages);
    }

    String getValidationMessage() {
        return validationMessage;
    }

    void setValidationMessage(String validationMessage) {
        this.validationMessage = validationMessage == null || validationMessage.isBlank()
                ? null
                : validationMessage.trim();
    }

    boolean hasValidationIssue() {
        return validationMessage != null;
    }

    int getResolvedPageCount() {
        return resolvedPages.size();
    }
}
