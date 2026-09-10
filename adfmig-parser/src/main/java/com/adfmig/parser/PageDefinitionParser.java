package com.adfmig.parser;

import com.adfmig.core.model.ScreenBinding;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses an ADF Faces page definition: what a screen asked of the model.
 *
 * <p>The same document format the REST resources use, without {@code usageMode="RESTClient"}. It
 * binds a screen to view instances and application module methods, and records which attributes
 * the screen displayed — so it is a record of business operations rather than of presentation, and
 * it is what an API for a rebuilt front end can be derived from.
 */
final class PageDefinitionParser {

    private final XmlDocuments documents = new XmlDocuments();

    Optional<ScreenBinding> parse(Path file, String relativePath) {
        return documents.loadRoot(file).map(root -> {
            List<Element> iterators = Xml.child(root, "executables")
                    .map(e -> Xml.children(e, "iterator"))
                    .orElse(List.of());

            List<ScreenBinding.Collection> collections = new ArrayList<>();
            Element bindings = Xml.child(root, "bindings").orElse(null);
            if (bindings != null) {
                for (Element tree : Xml.children(bindings, "tree")) {
                    // A tree names its iterator, and the iterator names the view instance.
                    Element iterator = iterators.stream()
                            .filter(i -> java.util.Objects.equals(
                                    Xml.attr(i, "id"), Xml.attr(tree, "IterBinding")))
                            .findFirst()
                            .orElse(null);

                    Element node = Xml.child(tree, "nodeDefinition").orElse(null);
                    collections.add(new ScreenBinding.Collection(
                            Xml.attr(tree, "id"),
                            iterator == null ? null : Xml.attr(iterator, "Binds"),
                            iterator == null ? null : Xml.attr(iterator, "DataControl"),
                            iterator == null ? null : Xml.intAttr(iterator, "RangeSize"),
                            node == null ? null : Xml.attr(node, "DefName"),
                            node == null ? List.of() : Xml.attrArray(node, "AttrNames")));
                }
            }

            List<String> operations = bindings == null ? List.of()
                    : Xml.children(bindings, "methodAction").stream()
                            .map(m -> Xml.attr(m, "MethodName", Xml.attr(m, "id")))
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .toList();

            return new ScreenBinding(Xml.attr(root, "id"), List.copyOf(collections),
                    operations, relativePath);
        });
    }
}
