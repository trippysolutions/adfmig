package com.adfmig.parser;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Loads an ADF XML document into a DOM tree.
 *
 * <p>Two hazards are handled here, both learned from real applications rather than from the spec:
 *
 * <ul>
 *   <li>Every ADF business component declares an external DTD
 *       ({@code <!DOCTYPE Entity SYSTEM "jbo_03_01.dtd">}) that is not shipped with the
 *       application. Resolution must be suppressed: offline it fails, and inside an air-gapped
 *       customer network it stalls. Refusing external entities also closes XXE, which a bank's
 *       security review will ask about.
 *   <li>Some documents declare Java charset names that conformant parsers reject. See
 *       {@link XmlEncodings}.
 * </ul>
 *
 * <p>Not thread-safe: {@link DocumentBuilder} makes no thread-safety guarantee, so each instance
 * owns its own. Create one loader per parsing thread.
 */
final class XmlDocuments {

    private final DocumentBuilder builder;

    XmlDocuments() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            builder = factory.newDocumentBuilder();
            // Belt and braces: any entity that survives the features above resolves to nothing
            // rather than reaching the network or the filesystem.
            builder.setEntityResolver((publicId, systemId) ->
                    new InputSource(new ByteArrayInputStream(new byte[0])));
            builder.setErrorHandler(null);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("cannot configure a safe XML parser", e);
        }
    }

    /** @return the document's root element, or empty when the file cannot be parsed */
    Optional<Element> loadRoot(Path file) {
        try {
            Document document;
            try (var in = Files.newInputStream(file)) {
                document = builder.parse(in);
            } catch (SAXException first) {
                Optional<String> recovered = XmlEncodings.recover(Files.readAllBytes(file));
                if (recovered.isEmpty()) return Optional.empty();
                document = builder.parse(new InputSource(new StringReader(recovered.get())));
            }
            return Optional.ofNullable(document.getDocumentElement());
        } catch (SAXException | IOException e) {
            return Optional.empty();
        }
    }
}
