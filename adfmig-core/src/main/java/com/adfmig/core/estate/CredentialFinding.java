package com.adfmig.core.estate;

/**
 * Credential material found in application source, recorded by location only.
 *
 * <p>The value is never read, stored or exported. Reversing Oracle's obfuscation would turn a
 * migration tool into a credential harvester, which is both wrong and disqualifying at the banks
 * and government departments this is sold to. Their presence, though, is a genuine finding: these
 * secrets sit in source control and are almost always still live.
 *
 * @param path        file containing the material, relative to the estate root
 * @param location    where inside the file, by element or attribute name — never the value
 * @param occurrences how many were found in that file
 */
public record CredentialFinding(String path, Kind kind, String location, int occurrences) {

    public enum Kind {
        /**
         * An Oracle-obfuscated secret, written with a {@code {903}} or {@code {904}} prefix.
         *
         * <p>This is obfuscation, not encryption: it is reversible with Oracle's own libraries, so
         * anyone who can read the file can read the password. Treat these as plaintext secrets
         * committed to source control — they must be rotated, not carried across.
         */
        OBFUSCATED_SECRET("obfuscated secret (reversible — treat as plaintext)"),

        /** An Oracle wallet or credential store holding secrets outside the XML. */
        CREDENTIAL_STORE("Oracle wallet / credential store"),

        /** A keystore or truststore. */
        KEYSTORE("keystore");

        private final String description;

        Kind(String description) { this.description = description; }

        public String description() { return description; }
    }
}
