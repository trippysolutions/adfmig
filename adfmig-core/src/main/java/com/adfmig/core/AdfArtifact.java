package com.adfmig.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One discovered artifact of an ADF application.
 *
 * <p>This is the scan-level record. Deep parsing (attributes, queries, validators, permissions)
 * attaches to the richer per-type models and is keyed back to the {@link #path()} recorded here.
 *
 * @param path         path relative to the scanned application root, using {@code /} separators
 * @param type         classified artifact type
 * @param rootElement  root element for XML artifacts, {@code null} otherwise
 * @param name         the artifact's declared {@code Name}/{@code id}, when it has one
 * @param attributes   root element attributes, retained for later analysis passes
 */
public record AdfArtifact(
        String path,
        AdfArtifactType type,
        String rootElement,
        String name,
        Map<String, String> attributes) {

    public static AdfArtifact of(String path, AdfArtifactType type, String rootElement, XmlHeader header) {
        String name = header.attr("Name");
        if (name == null) name = header.attr("id");
        return new AdfArtifact(path, type, rootElement, name, redact(header.attributes()));
    }

    /**
     * Removes anything that looks like a secret before it can be retained or exported.
     *
     * <p>Attributes are captured wholesale for later analysis, and a scan result is written to
     * JSON and handed to customers. Nothing in this tool needs a secret's value, so any that
     * appears is replaced rather than carried: the cost of the check is nothing and the cost of
     * leaking a customer credential into a report is considerable.
     */
    private static Map<String, String> redact(Map<String, String> attributes) {
        Map<String, String> safe = new LinkedHashMap<>(attributes.size());
        attributes.forEach((key, value) -> safe.put(key, isSecret(key, value) ? REDACTED : value));
        return Map.copyOf(safe);
    }

    private static boolean isSecret(String key, String value) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("password") || lower.contains("credential")
                || lower.contains("secret") || lower.equals("pwd")) {
            return true;
        }
        // Oracle writes obfuscated secrets with a three digit algorithm prefix, e.g. {904}.
        return value != null && value.length() > 5
                && value.charAt(0) == '{' && value.charAt(4) == '}'
                && Character.isDigit(value.charAt(1)) && Character.isDigit(value.charAt(2))
                && Character.isDigit(value.charAt(3));
    }

    /** Placeholder written in place of any value that looks like a secret. */
    public static final String REDACTED = "[redacted]";

    public static AdfArtifact ofNonXml(String path, AdfArtifactType type) {
        return new AdfArtifact(path, type, null, null, Map.of());
    }
}
