package com.adfmig.parser;

import com.adfmig.core.model.SecurityPolicy;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Parses {@code jazn-data.xml}: the application's authorization policy.
 *
 * <p>The structure is
 * {@code policy-store > applications > application > jazn-policy > grant}, where each grant pairs
 * a set of principals with a set of permissions. A permission names its class, the resource, and
 * the actions allowed — and for {@code RestServicePermission} those three facts are exactly what a
 * Spring Security rule needs, with nothing left to infer.
 */
final class SecurityParser {

    private final XmlDocuments documents = new XmlDocuments();

    Optional<SecurityPolicy> parse(Path file, String relativePath) {
        return documents.loadRoot(file).map(root -> {
            List<SecurityPolicy.Grant> grants = new ArrayList<>();
            String applicationName = null;

            for (Element application : Xml.descendants(root, "application")) {
                String name = Xml.child(application, "name").map(Xml::text).orElse(null);
                if (name != null && applicationName == null) applicationName = name;

                for (Element grant : Xml.descendants(application, "grant")) {
                    grants.addAll(grantsIn(grant));
                }
            }

            return new SecurityPolicy(applicationName, grants, users(root), relativePath);
        });
    }

    /**
     * Expands one grant into a flat list. A grant may name several principals and several
     * permissions at once, meaning every principal holds every permission; flattening keeps the
     * downstream role-by-endpoint matrix simple.
     */
    private List<SecurityPolicy.Grant> grantsIn(Element grant) {
        record Principal(String type, String name) {}

        List<Principal> principals = Xml.descendants(grant, "principal").stream()
                .map(p -> new Principal(
                        Xml.child(p, "class").map(Xml::text).orElse(null),
                        Xml.child(p, "name").map(Xml::text).orElse(null)))
                .toList();

        List<SecurityPolicy.Grant> out = new ArrayList<>();
        for (Element permission : Xml.descendants(grant, "permission")) {
            String permissionClass = Xml.child(permission, "class").map(Xml::text).orElse(null);
            String resourceName = Xml.child(permission, "name").map(Xml::text).orElse(null);
            List<String> actions = splitActions(Xml.child(permission, "actions").map(Xml::text).orElse(null));

            for (Principal principal : principals) {
                out.add(new SecurityPolicy.Grant(
                        principal.type(), principal.name(), permissionClass, resourceName, actions));
            }
        }
        return out;
    }

    /** Actions arrive either one per permission element or comma-separated within one. */
    private static List<String> splitActions(String actions) {
        if (actions == null || actions.isBlank()) return List.of();
        return Arrays.stream(actions.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Users defined in the file's own identity store. Their presence means the application
     * authenticates against a file rather than a directory — a development shortcut that cannot
     * be carried into the migrated application.
     */
    private List<String> users(Element root) {
        return Xml.descendants(root, "user").stream()
                .map(u -> Xml.child(u, "name").map(Xml::text).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
