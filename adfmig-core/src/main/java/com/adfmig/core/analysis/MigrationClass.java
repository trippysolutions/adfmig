package com.adfmig.core.analysis;

/**
 * How much of an artifact a generator can produce, and therefore what it costs.
 *
 * <p>The distinction that matters commercially is between work a tool does and work a person does.
 * Declarative metadata converts mechanically; hand-written Java and Groovy do not, and pretending
 * otherwise is how migration projects overrun.
 */
public enum MigrationClass {

    /**
     * Fully described by metadata. The generator produces working code with nothing left to
     * decide — an entity object with declarative validation and no custom class, or a view object
     * whose query ADF generated from its own attributes.
     */
    AUTO("generated, no manual work"),

    /**
     * Generated, but a person must finish it. Hand-written SQL carried across as a native query,
     * a Groovy expression translated, or a small custom class reviewed. The structure is free;
     * the judgement is not.
     */
    ASSISTED("generated with review needed"),

    /**
     * Hand-written against the ADF runtime. Code calling {@code getDBTransaction},
     * {@code RowSetIterator} or runtime view criteria has no mechanical equivalent, so a person
     * rewrites it against the generated model.
     */
    MANUAL("rewritten by hand"),

    /**
     * Bound to the ADF Faces UI. Cannot be ported to REST at all — the consumer has to be rebuilt.
     * Almost always excluded from a backend migration estimate and quoted separately.
     */
    REWRITE("front end rebuilt separately");

    private final String description;

    MigrationClass(String description) { this.description = description; }

    public String description() { return description; }
}
