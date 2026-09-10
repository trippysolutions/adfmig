package com.adfmig.parser;

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
import java.util.Set;

/**
 * Finds the individual ADF applications inside a directory tree.
 *
 * <p>A JDeveloper workspace file ({@code .jws}) marks one application: one deployable unit, one
 * set of business components, one security policy. Everything else — projects, models, view
 * controllers — sits beneath it.
 */
public final class ApplicationDiscovery {

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", ".svn", ".hg", "target", "build", "node_modules", "classes", ".idea", ".settings");

    /** One application root, with the workspace file that identified it. */
    public record Found(Path root, Path workspaceFile, String name) {}

    /**
     * @return every application found, ordered by path. When the tree contains no workspace file
     *         the tree itself is treated as a single application, which is the right answer for
     *         source exported without its {@code .jws}.
     */
    public List<Found> discover(Path estateRoot) throws IOException {
        Path root = estateRoot.toAbsolutePath().normalize();
        List<Path> workspaces = findWorkspaces(root);

        if (workspaces.isEmpty()) {
            return List.of(new Found(root, null, root.getFileName().toString()));
        }

        // Several workspaces can sit in one directory; that directory is still one application
        // root, so collapse them and keep the first workspace as the identifying file.
        Map<Path, Path> byDirectory = new LinkedHashMap<>();
        for (Path workspace : workspaces) {
            byDirectory.putIfAbsent(workspace.getParent(), workspace);
        }

        List<Found> found = new ArrayList<>();
        byDirectory.forEach((directory, workspace) ->
                found.add(new Found(directory, workspace, stripExtension(workspace.getFileName().toString()))));
        found.sort((a, b) -> a.root().compareTo(b.root()));
        return found;
    }

    private List<Path> findWorkspaces(Path root) throws IOException {
        List<Path> workspaces = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                return SKIP_DIRS.contains(name) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jws")) {
                    workspaces.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
        workspaces.sort(Path::compareTo);
        return workspaces;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
