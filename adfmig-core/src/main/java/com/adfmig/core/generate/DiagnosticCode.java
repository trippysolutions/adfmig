package com.adfmig.core.generate;

/**
 * Every distinct thing the generator can report, with a stable identifier.
 *
 * <p>The identifier matters as much as the message. A developer finishing a migration needs to
 * filter thousands of lines down to "show me everything I still have to write", grep the generated
 * source for the matching marker, and count what is left. Free text cannot be counted or filtered;
 * a code can.
 *
 * <p>Codes are permanent once published. Renaming one breaks a customer's checklist.
 */
public enum DiagnosticCode {

    // --- Produced -------------------------------------------------------------------

    ENTITY_GENERATED(Severity.INFO, "JPA entity generated"),
    REPOSITORY_GENERATED(Severity.INFO, "Spring Data repository generated"),
    CONTROLLER_GENERATED(Severity.INFO, "REST controller generated"),
    SECURITY_GENERATED(Severity.INFO, "Spring Security configuration generated"),
    PROJECT_GENERATED(Severity.INFO, "Project scaffolding generated"),

    /** An Oracle runtime type was replaced by its Java equivalent. */
    ADF_TYPE_MAPPED(Severity.INFO, "ADF runtime type mapped to a Java type"),

    /** A declarative ADF rule became a Bean Validation constraint with the same meaning. */
    VALIDATION_TRANSLATED(Severity.INFO, "Declarative validation rule translated"),

    /** The application declared optimistic locking, so a version column was added. */
    VERSION_COLUMN_ADDED(Severity.INFO, "Optimistic locking preserved with a version column"),

    // --- Produced, but a person must finish it --------------------------------------

    /**
     * A Groovy expression was found and left untranslated. The original is copied into the
     * generated source beside a stub. The tool does not guess at business logic.
     */
    GROOVY_NOT_TRANSLATED(Severity.WARNING, "Groovy expression needs rewriting by hand"),

    /** A custom ADF Java class exists but its source was not found, so it could not be described. */
    CUSTOM_JAVA_NOT_TRANSLATED(Severity.WARNING, "Custom ADF Java class could not be read"),

    /**
     * A custom method whose shape has a direct equivalent — applying a view criteria and counting
     * rows, reading a sequence. Small, known work rather than a rewrite.
     */
    CUSTOM_JAVA_HAS_EQUIVALENT(Severity.WARNING, "Custom method has a direct equivalent"),

    /**
     * An operation ADF published to remote callers but implemented in Java. Its signature is
     * preserved and its body throws, so a caller cannot mistake it for working.
     */
    CUSTOM_OPERATION_NOT_IMPLEMENTED(Severity.WARNING, "Published operation has no implementation"),

    /** A custom method using ADF calls that map onto Spring, but which need reading first. */
    CUSTOM_JAVA_NEEDS_REVIEW(Severity.WARNING, "Custom method needs review before translating"),

    /**
     * A custom method reaching into ADF's own transaction or row-set machinery, or overriding a
     * hook ADF calls inside its write path. Nothing outside ADF corresponds to it.
     */
    CUSTOM_JAVA_NEEDS_REWRITE(Severity.WARNING, "Custom method must be rewritten by hand"),

    /** Hand-written SQL was carried across unchanged as a native query. */
    EXPERT_SQL_CARRIED_OVER(Severity.WARNING, "Hand-written SQL carried over as a native query"),

    /** A validation rule delegates to an expression and could not be turned into a constraint. */
    VALIDATION_NOT_TRANSLATED(Severity.WARNING, "Validation rule could not be translated"),

    /** The association behind a relationship is missing or incomplete, so no mapping was written. */
    ASSOCIATION_UNRESOLVED(Severity.WARNING, "Relationship could not be mapped"),

    /**
     * No grant covers this endpoint, so it was generated denied. In ADF an ungranted resource is
     * unreachable; publishing it open would turn a closed door into an open one.
     */
    ENDPOINT_DENIED_BY_DEFAULT(Severity.WARNING, "Endpoint generated denied: no grant found"),

    /** Permissions are held by a named user rather than a role. Roles must be introduced. */
    USER_GRANT_NOT_ROLE(Severity.WARNING, "Permission granted to a user, not a role"),

    /**
     * Two published URLs resolve to the same query, so the generated controllers are identical
     * apart from their path. Both are kept, because both existed in ADF and dropping one would
     * remove an endpoint callers may use.
     */
    ENDPOINTS_SHARE_ONE_QUERY(Severity.WARNING, "Endpoints differ only by URL"),

    /**
     * ADF used a column for optimistic locking whose type JPA cannot use as a version. The column
     * is mapped read-only and no locking is enforced, which is a real behaviour change.
     */
    VERSION_COLUMN_UNSUPPORTED_TYPE(Severity.WARNING, "Optimistic locking not carried over"),

    /**
     * A write comparison was generated but has no row to post. Only someone who knows the
     * application knows what inserts cleanly, so the generator leaves it empty rather than
     * inventing a row that fails against the database instead of against the migration.
     */
    CONTRACT_TEST_NEEDS_A_ROW(Severity.WARNING, "Write comparison needs a row to post"),

    /**
     * A view object joins several entities and this attribute belongs to one that is not the
     * entity behind it, so it cannot be read from that entity and is returned as null.
     */
    JOINED_ATTRIBUTE_NOT_MAPPED(Severity.WARNING, "Attribute from a joined entity not mapped"),

    /**
     * An endpoint generated for an ADF Faces application from what its screens read. The URL is
     * new: the original published nothing over HTTP, so nothing is preserved and nothing compares
     * it against the original.
     */
    SCREEN_DERIVED_ENDPOINT(Severity.WARNING, "New endpoint, derived from a screen"),

    /** The database connection must be supplied by configuration; no credential was carried over. */
    DATASOURCE_NEEDS_CONFIGURATION(Severity.WARNING, "Database connection left to configuration"),

    // --- Refused ---------------------------------------------------------------------

    /** JPA has no identity to map. Inventing a key would change what the application means. */
    NO_PRIMARY_KEY(Severity.SKIPPED, "Skipped: entity has no primary key"),

    /** The view object is populated by Java code, not by a query. Nothing to generate from. */
    PROGRAMMATIC_VIEW_OBJECT(Severity.SKIPPED, "Skipped: view object is populated by code"),

    /** The chain from URL to query is broken in the source application. */
    ENDPOINT_UNRESOLVED(Severity.SKIPPED, "Skipped: endpoint does not resolve to a query"),

    /** An attribute carries no column, so there is nothing to map it to. */
    ATTRIBUTE_HAS_NO_COLUMN(Severity.SKIPPED, "Skipped: attribute has no column"),

    /**
     * The application exposes a nested collection through a view link, and the generated API does
     * not. The URL convention for a child collection is ADF's own, and inventing one would publish
     * a contract the original never had.
     */
    MASTER_DETAIL_NOT_EXPOSED(Severity.SKIPPED, "Skipped: nested collection not exposed"),

    /**
     * The file has changed since the generator wrote it, so it was left alone. Overwriting would
     * destroy the finishing work the generator itself asked a person to do.
     */
    FILE_EDITED_NOT_OVERWRITTEN(Severity.SKIPPED, "Skipped: file was edited since it was generated"),

    // --- Failed ----------------------------------------------------------------------

    WRITE_FAILED(Severity.ERROR, "Could not write the generated file");

    private final Severity severity;
    private final String summary;

    DiagnosticCode(Severity severity, String summary) {
        this.severity = severity;
        this.summary = summary;
    }

    public Severity severity() { return severity; }

    public String summary() { return summary; }
}
