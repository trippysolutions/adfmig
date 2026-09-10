package com.adfmig.core.generate;

/** How much attention a generator diagnostic needs. */
public enum Severity {

    /** Generated correctly and completely. Nothing to do. */
    INFO("ok"),

    /**
     * Generated, but a person must finish or check it. The generated source carries a matching
     * marker at the exact place that needs attention.
     */
    WARNING("review"),

    /**
     * Deliberately not generated, because the source application does not say enough to generate
     * it correctly. This is a refusal, not a failure: writing a guess would be worse than writing
     * nothing, because a guess looks finished.
     */
    SKIPPED("skipped"),

    /** The generator itself failed. This is a defect in the tool or the environment. */
    ERROR("error");

    private final String label;

    Severity(String label) { this.label = label; }

    public String label() { return label; }
}
