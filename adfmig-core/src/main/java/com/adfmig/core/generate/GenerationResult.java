package com.adfmig.core.generate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The complete record of one generation run: every file written and everything the generator could
 * not finish on its own.
 *
 * <p>Generation is deterministic. The same application produces the same files and the same
 * diagnostics on every run, with no network access and no external service involved. That is what
 * makes the output reviewable in a pull request and repeatable in a build.
 *
 * <p>The tool never guesses. Where the source application does not say enough to generate correct
 * code, it writes a stub, copies the original ADF definition into a comment beside it, marks the
 * spot, and records it here — because a guess that looks finished is worse than an obvious gap.
 */
public record GenerationResult(
        String applicationName,
        String outputDirectory,
        List<String> filesWritten,
        List<Diagnostic> diagnostics) {

    public static GenerationResult of(String applicationName, String outputDirectory,
                                      List<String> filesWritten, List<Diagnostic> diagnostics) {
        return new GenerationResult(applicationName, outputDirectory,
                List.copyOf(filesWritten), List.copyOf(diagnostics));
    }

    public List<Diagnostic> of(Severity severity) {
        return diagnostics.stream().filter(d -> d.severity() == severity).toList();
    }

    /** Everything a person still has to act on, most serious first. */
    public List<Diagnostic> needingAttention() {
        List<Diagnostic> out = new ArrayList<>();
        out.addAll(of(Severity.ERROR));
        out.addAll(of(Severity.SKIPPED));
        out.addAll(of(Severity.WARNING));
        return out;
    }

    public Map<Severity, Integer> countsBySeverity() {
        Map<Severity, Integer> counts = new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            int count = of(severity).size();
            if (count > 0) counts.put(severity, count);
        }
        return counts;
    }

    /** Diagnostics grouped by code, so the same finding repeated many times reads as one line. */
    public Map<DiagnosticCode, List<Diagnostic>> byCode() {
        Map<DiagnosticCode, List<Diagnostic>> grouped = new LinkedHashMap<>();
        diagnostics.forEach(d -> grouped.computeIfAbsent(d.code(), k -> new ArrayList<>()).add(d));
        return grouped;
    }

    /** True when everything generated is complete and needs no further work. */
    public boolean isComplete() {
        return diagnostics.stream().noneMatch(Diagnostic::needsAttention);
    }

    public boolean hasErrors() {
        return !of(Severity.ERROR).isEmpty();
    }
}
