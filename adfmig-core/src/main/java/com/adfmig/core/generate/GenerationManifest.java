package com.adfmig.core.generate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A record of what the generator wrote and what it looked like, kept beside the generated project.
 *
 * <p>Generated files say they are the customer's to edit, and that regenerating overwrites them.
 * The manifest is how the tool keeps the second half of that promise honest: on the next run it
 * compares each file against the content it wrote, and a file that has changed since is left
 * alone. Without it, one careless regeneration silently destroys a week of finishing work — the
 * exact work the generator asked a person to do.
 */
public final class GenerationManifest {

    /** Kept at the root of the generated project. */
    public static final String FILE_NAME = ".adfmig-manifest";

    private final Map<String, String> hashesByPath;

    private GenerationManifest(Map<String, String> hashesByPath) {
        this.hashesByPath = hashesByPath;
    }

    public static GenerationManifest empty() {
        return new GenerationManifest(new LinkedHashMap<>());
    }

    /** Reads the manifest from a previous run, or an empty one when there was none. */
    public static GenerationManifest read(Path projectRoot) {
        Path file = projectRoot.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return empty();

        Map<String, String> hashes = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int separator = line.indexOf("  ");
                if (separator > 0) {
                    hashes.put(line.substring(separator + 2).trim(), line.substring(0, separator).trim());
                }
            }
        } catch (IOException e) {
            // An unreadable manifest must not stop a generation; it only means nothing can be
            // recognised as previously generated, so nothing will be overwritten.
            return empty();
        }
        return new GenerationManifest(hashes);
    }

    public void record(String path, String content) {
        hashesByPath.put(path, hash(content));
    }

    /**
     * Whether a file on disk still holds exactly what the generator last wrote there.
     *
     * @return true when the file is unchanged and may be overwritten
     */
    public boolean isUnchanged(Path projectRoot, String path) {
        String recorded = hashesByPath.get(path);
        if (recorded == null) return false;
        try {
            return recorded.equals(hash(Files.readString(projectRoot.resolve(path), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return false;
        }
    }

    /** Whether this path was written by a previous run at all. */
    public boolean wasGenerated(String path) {
        return hashesByPath.containsKey(path);
    }

    public void write(Path projectRoot, String toolVersion) throws IOException {
        StringBuilder out = new StringBuilder(hashesByPath.size() * 80 + 256);
        out.append("# adfmig generation manifest. Do not edit.\n")
                .append("# Written by adfmig ").append(toolVersion)
                .append(" on ").append(Instant.now()).append(".\n")
                .append("# Regenerating leaves any file that has changed since it was written.\n");
        hashesByPath.forEach((path, hash) -> out.append(hash).append("  ").append(path).append('\n'));
        Files.writeString(projectRoot.resolve(FILE_NAME), out.toString(), StandardCharsets.UTF_8);
    }

    private static String hash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the platform", e);
        }
    }
}
