package com.adfmig.parser;

import com.adfmig.core.estate.CredentialFinding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds credential material committed to application source.
 *
 * <p>Only locations are recorded. Secret values are never read into the model, and no attempt is
 * made to reverse Oracle's obfuscation. What the scan produces is a list of files to rotate and
 * remove from source control, which is a finding customers act on.
 */
final class CredentialScanner {

    /**
     * Oracle writes obfuscated secrets with a three digit algorithm prefix, {@code {903}} for the
     * identity store and {@code {904}} for connection passwords.
     */
    private static final Pattern OBFUSCATED = Pattern.compile("\\{9\\d{2}}");

    /**
     * The element or attribute immediately enclosing an obfuscated value.
     *
     * <p>The value may sit directly in element text, inside a CDATA section — which is how
     * {@code bc4j.xcfg} writes connection passwords — or in an attribute.
     */
    private static final Pattern ENCLOSING = Pattern.compile(
            "(?:<([A-Za-z_][\\w:.-]*)[^>]*>\\s*(?:<!\\[CDATA\\[\\s*)?"
                    + "|([A-Za-z_][\\w:.-]*)\\s*=\\s*\")\\{9\\d{2}}");

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", ".svn", ".hg", "target", "build", "node_modules", "classes", ".idea", ".settings");

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".xml", ".xcfg", ".rpx", ".cpx", ".properties", ".jpx", ".jpr", ".jws", ".conf");

    private static final long MAX_TEXT_BYTES = 8L * 1024 * 1024;

    List<CredentialFinding> scan(Path applicationRoot) throws IOException {
        Path root = applicationRoot.toAbsolutePath().normalize();
        List<CredentialFinding> findings = new ArrayList<>();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                return SKIP_DIRS.contains(name) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                String relative = root.relativize(file).toString().replace('\\', '/');

                CredentialFinding.Kind byName = kindOf(lower);
                if (byName != null) {
                    findings.add(new CredentialFinding(relative, byName, name, 1));
                    return FileVisitResult.CONTINUE;
                }

                if (isTextCandidate(lower) && attrs.size() <= MAX_TEXT_BYTES) {
                    obfuscatedIn(file, relative).ifPresent(findings::add);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
        return findings;
    }

    private static java.util.Optional<CredentialFinding> obfuscatedIn(Path file, String relative) {
        try {
            // Read as ISO-8859-1: the marker is ASCII, so this matches under any encoding the file
            // might actually use, and avoids a decode failure on legacy charsets.
            String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);

            int occurrences = 0;
            Matcher counter = OBFUSCATED.matcher(text);
            while (counter.find()) occurrences++;
            if (occurrences == 0) return java.util.Optional.empty();

            Set<String> locations = new LinkedHashSet<>();
            Matcher enclosing = ENCLOSING.matcher(text);
            while (enclosing.find()) {
                String where = enclosing.group(1) != null ? enclosing.group(1) : enclosing.group(2);
                if (where != null) locations.add(where);
            }

            return java.util.Optional.of(new CredentialFinding(
                    relative,
                    CredentialFinding.Kind.OBFUSCATED_SECRET,
                    locations.isEmpty() ? "unknown" : String.join(", ", locations),
                    occurrences));
        } catch (IOException e) {
            return java.util.Optional.empty();
        }
    }

    private static CredentialFinding.Kind kindOf(String lowerName) {
        if (lowerName.startsWith("cwallet.sso") || lowerName.startsWith("ewallet")) {
            // The lock file alone carries nothing; the wallet beside it does.
            return lowerName.endsWith(".lck") ? null : CredentialFinding.Kind.CREDENTIAL_STORE;
        }
        if (lowerName.endsWith(".jks") || lowerName.endsWith(".p12")
                || lowerName.endsWith(".jceks") || lowerName.endsWith(".keystore")) {
            return CredentialFinding.Kind.KEYSTORE;
        }
        return null;
    }

    private static boolean isTextCandidate(String lowerName) {
        for (String extension : TEXT_EXTENSIONS) {
            if (lowerName.endsWith(extension)) return true;
        }
        return false;
    }
}
