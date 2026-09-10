package com.adfmig.parser;

import com.adfmig.core.model.RestRegistry;
import com.adfmig.core.model.RestResource;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses the ADF BC REST artifacts: the {@code ResourceRegistry.rpx} manifest and the individual
 * resource definitions it points at.
 */
final class RestParser {

    private final XmlDocuments documents = new XmlDocuments();

    /**
     * Parses {@code ResourceRegistry.rpx}: the list of published paths, the resource definitions
     * serving them, and the data controls those definitions bind through.
     */
    Optional<RestRegistry> parseRegistry(Path file, String relativePath) {
        return documents.loadRoot(file).map(root -> {
            List<RestRegistry.PathEntry> paths = Xml.descendants(root, "page").stream()
                    .filter(p -> Xml.attr(p, "path") != null && Xml.attr(p, "usageId") != null)
                    .map(p -> new RestRegistry.PathEntry(Xml.attr(p, "path"), Xml.attr(p, "usageId")))
                    .toList();

            List<RestRegistry.Usage> usages = Xml.child(root, "pageDefinitionUsages")
                    .map(u -> Xml.children(u, "page").stream()
                            .map(p -> new RestRegistry.Usage(Xml.attr(p, "id"), Xml.attr(p, "path")))
                            .toList())
                    .orElse(List.of());

            List<RestRegistry.DataControl> dataControls = Xml.child(root, "dataControlUsages")
                    .map(dcu -> Xml.children(dcu).stream()
                            .map(dc -> new RestRegistry.DataControl(
                                    Xml.attr(dc, "id"),
                                    Xml.attr(dc, "Package"),
                                    Xml.attr(dc, "Configuration")))
                            .toList())
                    .orElse(List.of());

            return new RestRegistry(Xml.attr(root, "Package"), paths, usages, dataControls, relativePath);
        });
    }

    /**
     * Parses one REST resource definition.
     *
     * <p>These are {@code pageDefinition} documents with {@code usageMode="RESTClient"}. The
     * iterator names the application module view instance backing the resource; each {@code tree}
     * binding is a collection in the payload; each {@code methodAction} is a custom operation,
     * with its parameter types declared as {@code NamedData} children.
     */
    Optional<RestResource> parseResource(Path file, String relativePath, String fqn) {
        return documents.loadRoot(file).map(root -> {
            Element bindings = Xml.child(root, "bindings").orElse(null);

            List<String> collections = bindings == null ? List.of()
                    : Xml.children(bindings, "tree").stream()
                            .map(t -> Xml.attr(t, "id"))
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .toList();

            List<RestResource.Operation> operations = bindings == null ? List.of()
                    : Xml.children(bindings, "methodAction").stream()
                            .map(RestParser::operation)
                            .toList();

            // The first non-variable iterator is the resource's backing view instance.
            String viewUsage = Xml.child(root, "executables")
                    .map(e -> Xml.children(e, "iterator"))
                    .orElse(List.of())
                    .stream()
                    .map(i -> Xml.attr(i, "Binds"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            String dataControl = Xml.child(root, "executables")
                    .map(e -> Xml.children(e, "iterator"))
                    .orElse(List.of())
                    .stream()
                    .map(i -> Xml.attr(i, "DataControl"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            Integer rangeSize = Xml.child(root, "executables")
                    .map(e -> Xml.children(e, "iterator"))
                    .orElse(List.of())
                    .stream()
                    .map(i -> Xml.intAttr(i, "RangeSize"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            return new RestResource(
                    Xml.attr(root, "id"), fqn, viewUsage, dataControl,
                    collections, operations, rangeSize, relativePath);
        });
    }

    private static RestResource.Operation operation(Element methodAction) {
        List<RestResource.Param> params = Xml.children(methodAction, "NamedData").stream()
                .map(nd -> new RestResource.Param(Xml.attr(nd, "NDName"), Xml.attr(nd, "NDType")))
                .toList();

        return new RestResource.Operation(
                Xml.attr(methodAction, "MethodName", Xml.attr(methodAction, "id")),
                Xml.boolAttr(methodAction, "IsViewObjectMethod"),
                Xml.attr(methodAction, "InstanceName"),
                params);
    }
}
