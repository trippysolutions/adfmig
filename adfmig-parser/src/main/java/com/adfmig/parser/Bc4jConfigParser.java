package com.adfmig.parser;

import com.adfmig.core.estate.DatabaseConnection;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses {@code bc4j.xcfg}: how each application module reaches its database.
 *
 * <p>Both declaration forms are handled — the development {@code ConnectionDefinition} carrying
 * host and user, and the deployed {@code JDBCDataSource} naming a JNDI datasource. Production
 * applications use the second, so a parser reading only the first reports most of a real estate as
 * having no database.
 *
 * <p>Passwords are not read. See {@link DatabaseConnection}.
 */
final class Bc4jConfigParser {

    private final XmlDocuments documents = new XmlDocuments();

    List<DatabaseConnection> parse(Path file, String relativePath) {
        return documents.loadRoot(file).map(root -> {
            List<DatabaseConnection> connections = new ArrayList<>();

            for (Element definition : Xml.descendants(root, "ConnectionDefinition")) {
                connections.add(DatabaseConnection.jdbc(
                        Xml.attr(definition, "name"),
                        entry(definition, "HOSTNAME"),
                        entry(definition, "JDBC_PORT"),
                        serviceName(definition),
                        entry(definition, "user"),
                        applicationModuleFor(definition, root),
                        lockingMode(root),
                        relativePath));
            }

            for (Element config : Xml.descendants(root, "AppModuleConfig")) {
                for (Element custom : Xml.children(config, "Custom")) {
                    // The attribute is namespace-qualified, and the prefix is not stable.
                    String dataSource = Xml.attrLocal(custom, "JDBCDataSource");
                    if (dataSource == null) continue;
                    connections.add(DatabaseConnection.dataSource(
                            Xml.attr(config, "name"),
                            dataSource,
                            Xml.attr(config, "ApplicationName"),
                            lockingMode(config),
                            relativePath));
                }
            }
            return connections;
        }).orElse(List.of());
    }

    /**
     * Reads {@code jbo.locking.mode}, declared on the {@code Database} element or as a property.
     * It decides whether generated entities need a version column: replacing an optimistically
     * locked application with one that does no locking loses concurrent updates silently.
     */
    private static String lockingMode(Element scope) {
        for (Element database : Xml.descendants(scope, "Database")) {
            String mode = Xml.attrLocal(database, "jbo.locking.mode");
            if (mode != null) return mode;
        }
        for (Element custom : Xml.descendants(scope, "Custom")) {
            String mode = Xml.attrLocal(custom, "jbo.locking.mode");
            if (mode != null) return mode;
        }
        return null;
    }

    /** A connection names its database by SID on older configurations, by service on newer ones. */
    private static String serviceName(Element definition) {
        String sid = entry(definition, "SID");
        return sid != null ? sid : entry(definition, "ServiceName");
    }

    /**
     * The application module a development connection serves. These configurations name the module
     * on the enclosing bag or config rather than on the connection itself.
     */
    private static String applicationModuleFor(Element definition, Element root) {
        String onDefinition = Xml.attr(definition, "ApplicationName");
        if (onDefinition != null) return onDefinition;
        return Xml.descendants(root, "AppModuleConfig").stream()
                .map(c -> Xml.attr(c, "ApplicationName"))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseGet(() -> Xml.descendants(root, "ApplicationName").stream()
                        .map(Xml::text)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(null));
    }

    /** Reads {@code <ENTRY name=".." value=".."/>}, whose value may also be element text. */
    private static String entry(Element definition, String name) {
        return Xml.children(definition, "ENTRY").stream()
                .filter(e -> name.equals(Xml.attr(e, "name")))
                .findFirst()
                .map(e -> {
                    String value = Xml.attr(e, "value");
                    return value != null ? value : Xml.text(e);
                })
                .orElse(null);
    }
}
