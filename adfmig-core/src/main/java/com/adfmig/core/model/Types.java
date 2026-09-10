package com.adfmig.core.model;

import java.util.Map;

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
            Map.entry("oracle.jbo.domain.Raw", "byte[]"));

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

    /** Last path segment of a type name, for display. */
    public static String simple(String type) {
        if (type == null) return "void";
        int i = type.lastIndexOf('.');
        return i < 0 ? type : type.substring(i + 1);
    }
}
