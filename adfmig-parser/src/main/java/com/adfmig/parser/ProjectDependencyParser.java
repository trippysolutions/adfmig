package com.adfmig.parser;

import org.w3c.dom.Element;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the dependencies a JDeveloper project ({@code .jpr}) declares on other projects.
 *
 * <p>The file is an IDE configuration document rather than ADF metadata, so its structure is
 * generic nested {@code hash} and {@code list} elements keyed by an {@code n} attribute. The block
 * that matters is:
 *
 * <pre>{@code
 * <hash n="oracle.ide.model.DependencyConfiguration">
 *   <list n="dependencyList">
 *     <hash>
 *       <hash n="dependable">
 *         <url n="sourceOwnerURL" path="../OtherApp.jws"/>
 *         <url n="sourceURL" path="../Model/Model.jpr"/>
 * }</pre>
 *
 * <p>{@code sourceOwnerURL} naming a different workspace is what identifies a dependency reaching
 * across an application boundary — an ADF Library import.
 */
final class ProjectDependencyParser {

    private final XmlDocuments documents = new XmlDocuments();

    /** A raw dependency, with paths resolved against the declaring project's directory. */
    record Dependency(Path fromProject, Path toProject, Path owningWorkspace) {}

    List<Dependency> parse(Path projectFile) {
        Optional<Element> root = documents.loadRoot(projectFile);
        if (root.isEmpty()) return List.of();

        Path base = projectFile.getParent();
        List<Dependency> dependencies = new ArrayList<>();

        for (Element dependable : Xml.descendants(root.get(), "hash")) {
            if (!"dependable".equals(Xml.attr(dependable, "n"))) continue;

            Path target = resolve(base, urlPath(dependable, "sourceURL"));
            Path owner = resolve(base, urlPath(dependable, "sourceOwnerURL"));
            if (target != null) dependencies.add(new Dependency(projectFile, target, owner));
        }
        return dependencies;
    }

    private static String urlPath(Element dependable, String name) {
        return Xml.children(dependable, "url").stream()
                .filter(u -> name.equals(Xml.attr(u, "n")))
                .findFirst()
                .map(u -> Xml.attr(u, "path"))
                .orElse(null);
    }

    private static Path resolve(Path base, String relative) {
        if (relative == null || base == null) return null;
        try {
            return base.resolve(relative).normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
