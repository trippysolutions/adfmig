package com.adfmig.core.generate;

/**
 * One thing that happened during generation.
 *
 * <p>Each carries where it came from and where it went, so a developer can move between the ADF
 * source and the generated code without searching. Where a person has to act, {@code action} says
 * what to do rather than only what is wrong.
 *
 * @param code           stable identifier, also written into the generated source as a marker
 * @param sourceArtifact the ADF file this came from, relative to the application root
 * @param detail         what specifically was affected — an attribute, a method, a rule
 * @param generatedFile  the file written, or {@code null} when nothing was written
 * @param message        what happened, in one sentence
 * @param action         what the developer must do, or {@code null} when nothing is required
 */
public record Diagnostic(
        DiagnosticCode code,
        String sourceArtifact,
        String detail,
        String generatedFile,
        String message,
        String action) {

    public Severity severity() {
        return code.severity();
    }

    /** True when a person has to do something before the generated code is finished. */
    public boolean needsAttention() {
        return severity() == Severity.WARNING || severity() == Severity.SKIPPED
                || severity() == Severity.ERROR;
    }

    public static Diagnostic info(DiagnosticCode code, String sourceArtifact,
                                  String generatedFile, String message) {
        return new Diagnostic(code, sourceArtifact, null, generatedFile, message, null);
    }

    public static Diagnostic warning(DiagnosticCode code, String sourceArtifact, String detail,
                                     String generatedFile, String message, String action) {
        return new Diagnostic(code, sourceArtifact, detail, generatedFile, message, action);
    }

    public static Diagnostic skipped(DiagnosticCode code, String sourceArtifact, String detail,
                                     String message, String action) {
        return new Diagnostic(code, sourceArtifact, detail, null, message, action);
    }

    /**
     * The marker written into generated source at the place needing attention, so the report and
     * the code can be matched up by searching for the code.
     */
    public String marker() {
        return "TODO(adfmig:" + code.name() + ")" + (detail == null ? "" : " " + detail);
    }
}
