package com.adfmig.core.model;

import java.util.Map;
import java.util.Set;

/**
 * Maps ADF's declared attribute types onto the Java types a Spring Boot application would use.
 *
 * <p>The Oracle domain types are the ones that matter. {@code oracle.jbo.domain.Date},
 * {@code Number} and {@code ClobDomain} exist only inside the ADF runtime; carrying them across
 * unchanged would drag the ADF jars into the generated application, which is exactly the lock-in
 * a migration is meant to remove.
 */
public final class Types {

    private Types() {}

    private static final Map<String, String> ADF_TO_JAVA = Map.ofEntries(
            Map.entry("oracle.jbo.domain.Date", "java.time.LocalDate"),
            Map.entry("oracle.jbo.domain.Timestamp", "java.time.LocalDateTime"),
            Map.entry("oracle.jbo.domain.TimestampTZ", "java.time.OffsetDateTime"),
            Map.entry("oracle.jbo.domain.TimestampLTZ", "java.time.OffsetDateTime"),
            Map.entry("oracle.jbo.domain.Number", "java.math.BigDecimal"),
            Map.entry("oracle.jbo.domain.DBSequence", "java.lang.Long"),
            Map.entry("oracle.jbo.domain.RowID", "java.lang.String"),
            Map.entry("oracle.jbo.domain.ClobDomain", "java.lang.String"),
            Map.entry("oracle.jbo.domain.BlobDomain", "byte[]"),
            Map.entry("oracle.jbo.domain.Char", "java.lang.String"),
            Map.entry("oracle.jbo.domain.Raw", "byte[]"),

            // JDeveloper writes these straight into the entity for a DATE column, and they are
            // already plain Java, so they pass through untouched — carrying a time zone with
            // them. Oracle stores 2013-06-17 00:00:00 with no zone at all; serialised from a
            // Timestamp on a server three hours east of UTC it comes back as 2013-06-16, and a
            // hire date is silently a day early. The java.time types have no zone, so the value
            // that comes out is the value that was stored.
            Map.entry("java.sql.Date", "java.time.LocalDate"),
            Map.entry("java.sql.Timestamp", "java.time.LocalDateTime"),
            Map.entry("java.util.Date", "java.time.LocalDateTime"));

    /**
     * @return the Java type to use in generated code, or the input unchanged when it is already a
     *         plain Java type
     */
    public static String toJava(String adfType) {
        if (adfType == null) return null;
        return ADF_TO_JAVA.getOrDefault(adfType, adfType);
    }

    /** True when the type is an ADF runtime type with no equivalent outside ADF. */
    public static boolean isAdfDomainType(String type) {
        return type != null && type.startsWith("oracle.jbo.");
    }

    private static final Set<String> PORTABLE = Set.of(
            "byte[]", "boolean", "byte", "char", "short", "int", "long", "float", "double");

    /**
     * True when generated code can name this type.
     *
     * <p>The output compiles against the JDK and Spring and nothing else, so a type outside
     * {@code java.*} is an import that will not resolve — whether it is an ADF runtime type,
     * Oracle multimedia ({@code oracle.ord.im}), or a domain class the application declared for
     * itself. Those are common in older applications and each one made the generated project fail
     * to compile, which is worse than mapping it loosely and saying so.
     */
    /**
     * The type to write into generated code: the mapped Java type when there is one, and text
     * when the type exists only inside ADF. Every generator resolves through this, so the entity
     * field, its accessors, the record that publishes it and the repository's key type cannot
     * disagree about what an attribute is.
     */
    public static String toPortableJava(String adfType) {
        String mapped = toJava(adfType);
        return mapped == null || !isPortable(mapped) ? "java.lang.String" : mapped;
    }

    public static boolean isPortable(String javaType) {
        if (javaType == null) return false;
        String mapped = toJava(javaType);
        return mapped.startsWith("java.") || PORTABLE.contains(mapped);
    }

    /** Last path segment of a type name, for display. */
    public static String simple(String type) {
        if (type == null) return "void";
        int i = type.lastIndexOf('.');
        return i < 0 ? type : type.substring(i + 1);
    }
}
