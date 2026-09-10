package com.adfmig.parser;

import com.adfmig.core.AdfArtifact;
import com.adfmig.core.AdfArtifactType;
import com.adfmig.core.ScanResult;
import com.adfmig.core.XmlHeader;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Walks an ADF application root and classifies every artifact it contains.
 *
 * <p>This is the first pass. It answers "what is in here and what does it mean for a migration"
 * without parsing any artifact deeply, which keeps it fast enough to run across a whole estate.
 */
public final class ProjectScanner {

    /**
     * Extensions that may hold ADF metadata. ADF spreads XML across many extensions —
     * {@code .jpx} project files, {@code .cpx} bindings, {@code .rpx} REST registries,
     * {@code .xcfg} module configurations — so extension alone never identifies an artifact;
     * it only decides whether the file is worth opening.
     */
    private static final Set<String> XML_EXTENSIONS = Set.of(
            ".xml", ".rpx", ".cpx", ".xcfg", ".jpx", ".jpr", ".jws", ".dcx", ".sva", ".os_xml");

    /** Directories that never contain source artifacts. */
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", ".svn", ".hg", "target", "build", "node_modules", "classes", ".idea", ".settings");

    private final XmlHeaderReader headerReader = new XmlHeaderReader();

    public ScanResult scan(Path root) throws IOException {
        long start = System.nanoTime();
        List<AdfArtifact> artifacts = new ArrayList<>();
        Map<String, String> unparseable = new LinkedHashMap<>();
        int[] filesVisited = {0};

        Path absoluteRoot = root.toAbsolutePath().normalize();

        Files.walkFileTree(absoluteRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                return SKIP_DIRS.contains(name) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                filesVisited[0]++;
                String fileName = file.getFileName().toString();
                String relative = relativize(absoluteRoot, file);

                if (isXmlCandidate(fileName)) {
                    try {
                        Optional<XmlHeader> header = headerReader.read(file);
                        header.ifPresent(h -> artifacts.add(AdfArtifact.of(
                                relative, AdfArtifactType.classify(h, fileName), h.rootElement(), h)));
                    } catch (XmlHeaderReader.XmlReadException e) {
                        // Real applications contain malformed and truncated XML. Record it rather
                        // than failing the scan, and report it — an unreadable artifact is a
                        // migration risk the customer needs to know about.
                        unparseable.put(relative, e.reason());
                    }
                    return FileVisitResult.CONTINUE;
                }

                AdfArtifactType byExtension = AdfArtifactType.classifyByExtension(fileName);
                if (byExtension != null) {
                    artifacts.add(AdfArtifact.ofNonXml(relative, byExtension));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                unparseable.put(relativize(absoluteRoot, file), e.getClass().getSimpleName());
                return FileVisitResult.CONTINUE;
            }
        });

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        return new ScanResult(absoluteRoot.toString(), List.copyOf(artifacts),
                filesVisited[0], Map.copyOf(unparseable), durationMs);
    }

    private static boolean isXmlCandidate(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : XML_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private static String relativize(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
