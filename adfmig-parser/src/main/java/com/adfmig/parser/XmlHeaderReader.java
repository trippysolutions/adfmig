package com.adfmig.parser;

import com.adfmig.core.XmlHeader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads only the root element of an XML document.
 *
 * <p>Classification needs the root element and its attributes and nothing else, so this pulls
 * events until the first {@code START_ELEMENT} and stops. On a large application that is the
 * difference between reading a few hundred bytes per file and reading every file end to end.
 *
 * <p>Not thread-safe: {@link XMLInputFactory} makes no thread-safety guarantee, so each instance
 * owns its own factory. Create one reader per scanning thread.
 */
public final class XmlHeaderReader {

    private final XMLInputFactory factory;

    public XmlHeaderReader() {
        this.factory = XMLInputFactory.newInstance();

        // ADF business components files declare an external DTD:
        //     <!DOCTYPE AppModule SYSTEM "jbo_03_01.dtd">
        // The DTD is not shipped with the application. Tolerate the declaration so these documents
        // still parse, but never resolve anything external — inside an air-gapped customer network
        // a resolution attempt stalls the scan, and refusing external entities also closes XXE.
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, true);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_VALIDATING, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> InputStream.nullInputStream());
    }

    /** Files larger than this are not re-read whole on the legacy-encoding fallback path. */
    private static final long MAX_FALLBACK_BYTES = 16L * 1024 * 1024;

    /**
     * @return the document's root element and attributes, or empty if the file contains no elements
     * @throws XmlReadException carrying the underlying reason, so the caller can report which files
     *                          were skipped rather than silently dropping them
     */
    public Optional<XmlHeader> read(Path file) throws XmlReadException {
        try (InputStream in = Files.newInputStream(file)) {
            return firstElement(factory.createXMLStreamReader(in));
        } catch (XMLStreamException e) {
            return readWithLegacyEncoding(file, e);
        } catch (IOException e) {
            throw new XmlReadException(file, e);
        }
    }

    /**
     * Second attempt for documents JDeveloper wrote with a Java charset name rather than an IANA
     * one. See {@link XmlEncodings}. When nothing can be recovered, the original parse failure is
     * the honest answer and is rethrown.
     */
    private Optional<XmlHeader> readWithLegacyEncoding(Path file, XMLStreamException original)
            throws XmlReadException {
        try {
            if (Files.size(file) > MAX_FALLBACK_BYTES) throw original;

            Optional<String> recovered = XmlEncodings.recover(Files.readAllBytes(file));
            if (recovered.isEmpty()) throw original;

            return firstElement(factory.createXMLStreamReader(new StringReader(recovered.get())));
        } catch (XMLStreamException | IOException e) {
            throw new XmlReadException(file, e);
        }
    }

    private Optional<XmlHeader> firstElement(XMLStreamReader reader) throws XMLStreamException {
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    return Optional.of(new XmlHeader(
                            reader.getLocalName(),
                            reader.getNamespaceURI() == null ? "" : reader.getNamespaceURI(),
                            attributesOf(reader)));
                }
            }
            return Optional.empty();
        } finally {
            closeQuietly(reader);
        }
    }

    private static Map<String, String> attributesOf(XMLStreamReader reader) {
        int n = reader.getAttributeCount();
        Map<String, String> attrs = new LinkedHashMap<>(Math.max(4, n));
        for (int i = 0; i < n; i++) {
            attrs.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
        }
        return attrs;
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader == null) return;
        try {
            reader.close();
        } catch (Exception ignored) {
            // The underlying stream is closed by try-with-resources regardless.
        }
    }

    /** Signals that a candidate XML file could not be read, so the scan can report it. */
    public static final class XmlReadException extends Exception {
        private final transient Path file;

        XmlReadException(Path file, Throwable cause) {
            super(file + ": " + cause.getMessage(), cause);
            this.file = file;
        }

        public Path file() { return file; }

        /** The underlying reason, without the stack trace, for reporting. */
        public String reason() {
            Throwable cause = getCause();
            return cause == null ? getMessage() : cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
    }
}
