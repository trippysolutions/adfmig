package com.adfmig.core.model;

import java.util.List;

/**
 * One method of a hand-written ADF class, described well enough to price and to explain.
 *
 * @param lifecycleOverride the ADF lifecycle hook this overrides, or {@code null}. These are the
 *                          expensive ones: {@code doDML} and {@code prepareForDML} run inside
 *                          ADF's own write path, so their behaviour has to be re-hosted somewhere
 *                          Spring runs rather than simply copied.
 * @param adfApiCalls       ADF runtime calls the body makes, which is what cannot be carried over
 * @param recognisedPattern a known shape with a direct equivalent, when the body matches one
 */
public record CustomMethod(
        String name,
        String returnType,
        List<String> parameterTypes,
        String lifecycleOverride,
        List<String> adfApiCalls,
        String recognisedPattern,
        int lines) {

    /** How much a person has to do with this method. */
    public enum Effort {
        /** A known shape with a direct equivalent, or a body that touches nothing ADF-specific. */
        MECHANICAL,
        /** Uses the ADF runtime in ways that map onto Spring, but needs reading and deciding. */
        ASSISTED,
        /** Reaches into ADF's own transaction or row-set machinery. Rewritten from scratch. */
        MANUAL
    }

    public boolean isLifecycleOverride() {
        return lifecycleOverride != null;
    }

    public Effort effort() {
        if (recognisedPattern != null) return Effort.MECHANICAL;
        if (adfApiCalls.isEmpty() && !isLifecycleOverride()) return Effort.MECHANICAL;

        // Reaching for the transaction or a row set means the method is doing ADF's own work, and
        // there is no equivalent to translate it into.
        boolean deep = adfApiCalls.stream().anyMatch(call ->
                call.contains("getDBTransaction") || call.contains("RowSetIterator")
                        || call.contains("createRow") || call.contains("removeCurrentRow")
                        || call.contains("postChanges") || call.contains("doDML"));
        if (deep || "doDML".equals(lifecycleOverride) || "prepareForDML".equals(lifecycleOverride)) {
            return Effort.MANUAL;
        }
        return Effort.ASSISTED;
    }

    public String signature() {
        return name + "(" + String.join(", ", parameterTypes) + ")";
    }
}
