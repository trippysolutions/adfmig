package com.adfmig.parser;

import java.util.List;

/**
 * Derives an ADF component's fully qualified name from its file path.
 *
 * <p>ADF metadata files do not record their own package. A view object references its entity as
 * {@code com.example.model.entities.Employees} and an application module references view objects
 * the same way, so resolving those links means reconstructing each component's name from where it
 * sits under a source root.
 */
final class Fqn {

    private Fqn() {}

    /**
     * Source roots ADF projects use. The package begins immediately after one of these.
     * {@code adfmsrc} holds the binding and REST metadata; {@code src} holds business components.
     */
    private static final List<String> SOURCE_ROOTS =
            List.of("/adfmsrc/", "/adfmsource/", "/adfsrc/", "/src/", "/classes/");

    /**
     * @param relativePath path relative to the application root, using {@code /} separators
     * @return the fully qualified name, or {@code null} when the file is not under a source root
     */
    static String fromPath(String relativePath) {
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;

        // Take the DEEPEST source root, not the first. Repositories nest projects inside a source
        // directory — "/src/Pooling/Model/src/demo/Employees.xml" is a real shape — and matching
        // the outer "/src/" yields "Pooling.Model.src.demo.Employees" instead of "demo.Employees".
        // Wrong names mean references never resolve, which surfaces as every component appearing
        // to be dead code.
        int packageStart = -1;
        for (String root : SOURCE_ROOTS) {
            int index = path.lastIndexOf(root);
            if (index >= 0) packageStart = Math.max(packageStart, index + root.length());
        }
        if (packageStart < 0) return null;

        return toDotted(path.substring(packageStart));
    }

    private static String toDotted(String path) {
        int dot = path.lastIndexOf('.');
        String withoutExtension = dot < 0 ? path : path.substring(0, dot);
        return withoutExtension.replace('/', '.');
    }
}
