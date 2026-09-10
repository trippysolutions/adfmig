package com.adfmig.parser;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recovery for XML documents JDeveloper wrote with a Java charset name rather than an IANA one.
 *
 * <p>{@code encoding="Cp1252"}, {@code "MacRoman"} and — in Arabic locales — {@code "Cp1256"} are
 * all rejected outright by conformant XML parsers, but the JDK resolves them as charset aliases.
 * 52 files in a large sample of real applications are unreadable without this.
 *
 * <p>The recovery is: decode the bytes with the charset the document asked for, remove the
 * declaration the parser objected to, and parse the resulting text.
 */
final class XmlEncodings {

    private XmlEncodings() {}

    private static final Pattern ENCODING_DECL =
            Pattern.compile("<\\?xml[^>]*?encoding\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern XML_DECL =
            Pattern.compile("^\\s*<\\?xml.*?\\?>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Re-reads document bytes using the encoding they declare, with the declaration removed.
     *
     * @return the decoded text, or empty when no declared encoding is present or the JDK does not
     *         recognise it — in which case the original parse failure is the real answer
     */
    static Optional<String> recover(byte[] bytes) {
        // The declaration itself is ASCII under every encoding this can recover from.
        String prolog = new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.ISO_8859_1);
        Matcher m = ENCODING_DECL.matcher(prolog);
        if (!m.find()) return Optional.empty();

        Charset charset = resolve(m.group(1));
        if (charset == null) return Optional.empty();

        return Optional.of(XML_DECL.matcher(new String(bytes, charset)).replaceFirst(""));
    }

    /** @return the charset, or {@code null} when the JDK does not know the name */
    private static Charset resolve(String name) {
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
