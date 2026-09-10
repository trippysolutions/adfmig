package com.adfmig.core;

import java.util.Map;

/**
 * The identifying head of an XML document: its root element and that element's attributes.
 *
 * <p>ADF artifacts must be identified by root element rather than by filename. In a survey of
 * public ADF sample applications, {@code jazn-data} was the root element of 96 files but only 8
 * were actually named {@code jazn-data.xml}; a filename-driven scanner misses 92% of the security
 * configuration. Conversely, several distinct artifact types share a root element (both
 * {@code ResourceRegistry.rpx} and {@code DataBindings.cpx} are rooted at {@code Application}), so
 * classification also needs the attributes and the file extension.
 *
 * @param rootElement  local name of the root element, namespace prefix stripped
 * @param namespaceUri namespace URI of the root element, or {@code ""} when undeclared. ADF is
 *                     inconsistent here: some BC files declare {@code http://xmlns.oracle.com/bc4j}
 *                     and others declare nothing, so this is recorded but never required for
 *                     classification.
 * @param attributes   attributes of the root element, keyed by local name
 */
public record XmlHeader(String rootElement, String namespaceUri, Map<String, String> attributes) {

    public String attr(String name) {
        return attributes.get(name);
    }

    public boolean hasAttr(String name, String value) {
        return value.equals(attributes.get(name));
    }
}
