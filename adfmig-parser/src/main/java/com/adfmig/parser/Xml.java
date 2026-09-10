package com.adfmig.parser;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DOM navigation helpers that match on local name and ignore namespaces.
 *
 * <p>ADF is inconsistent about namespaces: business component files declare
 * {@code xmlns="http://xmlns.oracle.com/bc4j"}, validation rules within the same document sit in
 * {@code xmlns:validation}, and plenty of files declare nothing at all. Namespace-aware lookups
 * would need a different query per file vintage, so everything here matches on local name.
 */
final class Xml {

    private Xml() {}

    /** Direct child elements with the given local name. */
    static List<Element> children(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && localName.equals(localName(node))) {
                out.add((Element) node);
            }
        }
        return out;
    }

    /** All direct child elements. */
    static List<Element> children(Element parent) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) out.add((Element) node);
        }
        return out;
    }

    static Optional<Element> child(Element parent, String localName) {
        List<Element> found = children(parent, localName);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /** Descendant elements at any depth with the given local name. */
    static List<Element> descendants(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        collect(parent, localName, out);
        return out;
    }

    private static void collect(Element parent, String localName, List<Element> out) {
        for (Element child : children(parent)) {
            if (localName.equals(localName(child))) out.add(child);
            collect(child, localName, out);
        }
    }

    /** Local name of a node, with any namespace prefix stripped. */
    static String localName(Node node) {
        String local = node.getLocalName();
        if (local != null) return local;
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    /** @return the attribute value, or {@code null} when absent or empty */
    static String attr(Element element, String name) {
        String value = element.getAttribute(name);
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * Reads an attribute by local name, ignoring any namespace prefix.
     *
     * <p>Needed where ADF qualifies attributes, as in
     * {@code <Custom ns0:JDBCDataSource=".." xmlns:ns0="..."/>}: a plain lookup for
     * "JDBCDataSource" misses it, and the prefix is not stable across files.
     */
    static String attrLocal(Element element, String localName) {
        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (localName.equals(localName(attribute))) {
                String value = attribute.getNodeValue();
                return value == null || value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    static String attr(Element element, String name, String fallback) {
        String value = attr(element, name);
        return value == null ? fallback : value;
    }

    static boolean boolAttr(Element element, String name) {
        return "true".equalsIgnoreCase(element.getAttribute(name));
    }

    static Integer intAttr(Element element, String name) {
        String value = attr(element, name);
        if (value == null) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reads an ADF {@code DesignTime} property. ADF stores schema facts the runtime does not need
     * — constraint names, foreign key targets, check conditions — as {@code <Attr Name=".."
     * Value=".."/>} pairs inside a {@code DesignTime} block.
     */
    static String designTime(Element element, String attrName) {
        return child(element, "DesignTime")
                .flatMap(dt -> children(dt, "Attr").stream()
                        .filter(a -> attrName.equals(attr(a, "Name")))
                        .findFirst())
                .map(a -> attr(a, "Value"))
                .orElse(null);
    }

    /** Reads the {@code Item} values of a named {@code AttrArray}. */
    static List<String> attrArray(Element parent, String arrayName) {
        return children(parent, "AttrArray").stream()
                .filter(a -> arrayName.equals(attr(a, "Name")))
                .findFirst()
                .map(a -> children(a, "Item").stream()
                        .map(i -> attr(i, "Value"))
                        .filter(java.util.Objects::nonNull)
                        .toList())
                .orElse(List.of());
    }

    /** Text content, trimmed, or {@code null} when blank. */
    static String text(Element element) {
        String text = element.getTextContent();
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
