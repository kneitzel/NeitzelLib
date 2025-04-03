package de.neitzel.core.util;

import lombok.NonNull;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Utility class for handling common XML operations such as creating XML elements,
 * formatting XML strings, and validating XML content.
 */
@Slf4j
public class XmlUtils {
	/**
	 * Private constructor to prevent instantiation of the utility class.
	 * This utility class is not meant to be instantiated, as it only provides
	 * static utility methods for array-related operations.
	 *
	 * @throws UnsupportedOperationException always, to indicate that this class
	 *                                        should not be instantiated.
	 */
	private XmlUtils() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * A {@link DateTimeFormatter} instance used for formatting or parsing dates in the "yyyy-MM-dd" pattern.
	 *
	 * This formatter adheres to the XML date format standard (ISO-8601). It can be used
	 * to ensure consistent date representations in XML or other similar text-based formats.
	 *
	 * Thread-safe and immutable, this formatter can be shared across multiple threads.
	 */
	public static final DateTimeFormatter XML_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/**
	 * Creates a new element in the provided XML document with the specified name.
	 *
	 * @param doc  The XML document in which the new element is to be created. Must not be null.
	 * @param name The name of the element to be created. Must not be null or empty.
	 * @return The newly created element with the given name. Never returns null.
	 */
	public static Element createElement(final Document doc, final String name) {
		log.debug("Creating a new element  " + name);
		return doc.createElement(name);
	}

	/**
	 * Creates a new XML element with the specified name and value, appends the value
	 * as a text node to the element, and returns the resulting element.
	 *
	 * @param doc the XML document to which the element belongs. Must not be null.
	 * @param name the name of the element to create. Must not be null.
	 * @param value the text value to be set as the content of the created element.
	 *              If null, an empty string will be used as the content.
	 * @return the newly created XML element containing the specified value as a text node.
	 */
	public static Element createElement(final Document doc, final String name, final String value) {
		log.debug("Creating a new element  " + name + " with value: " + value);
		Element element = doc.createElement(name);

		Node content = doc.createTextNode(value != null ? value : "");
		element.appendChild(content);

		return element;
	}

	/**
	 * Creates an XML element with the specified name and value, formatted as a date string.
	 * The date value is converted to an XML-compatible string format using a predefined formatter.
	 *
	 * @param doc  the XML document to which the element belongs; must not be null
	 * @param name the name of the element to be created; must not be null or empty
	 * @param value the date value to be assigned to the created element; must not be null
	 * @return the created XML element containing the specified name and formatted date value
	 */
	public static Element createElement(final Document doc, final String name, final Date value) {
		return createElement(doc, name, XML_DATE_FORMATTER.format(value.toInstant()));
	}

	/**
	 * Creates an XML element with the specified name and optional value.
	 *
	 * @param doc   The Document object to which the element will belong. Must not be null.
	 * @param name  The name of the element to be created. Must not be null or empty.
	 * @param value The optional value to be set as the text content of the element. If null,
	 *              the element will have an empty text content.
	 * @return The newly created Element object with the specified name and value.
	 */
	public static Element createElement(final Document doc, final String name, final Integer value) {
		log.debug("Creating a new element  " + name + " with value: " + value);
		Element element = doc.createElement(name);

		Node content = doc.createTextNode(value != null ? ""+value : "");
		element.appendChild(content);

		return element;
	}

	/**
	 * Creates a new element with the specified name, appends it to the provided parent element,
	 * and returns the newly created element.
	 *
	 * @param doc The Document to which the new element belongs. Must not be null.
	 * @param name The name of the new element to be created. Must not be null or empty.
	 * @param parent The parent element to which the new element will be appended. Must not be null.
	 * @return The newly created and appended Element object.
	 */
	public static Element createAndInsertElement(final Document doc, final String name, @NonNull final Element parent) {
		log.debug("Creating a new element  " + name + " and adding it to a parent.");
		Element element = createElement(doc, name);
		parent.appendChild(element);
		return element;
	}

	/**
	 * Creates a new XML element with a specified name and value, adds it to the specified parent element,
	 * and returns the created element.
	 *
	 * @param doc The XML document to which the new element belongs. Must not be null.
	 * @param name The name of the element to create. Must not be null.
	 * @param value The value to be set for the created element. Can be null if no value is required.
	 * @param parent The parent element to which the new element will be appended. Must not be null.
	 * @return The newly created and inserted element.
	 */
	public static Element createAndInsertElement(final Document doc, final String name, final String value, @NonNull final Element parent) {
		log.debug("Creating a new element  " + name + " with value: " + value + " and adding it to a parent.");
		Element element = createElement(doc, name, value);
		parent.appendChild(element);
		return element;
	}


	/**
	 * Formats a given XML string by applying proper indentation to enhance readability.
	 * The method uses a transformer to process the XML input, applying indentation
	 * with a four-space configuration. In case of an error, the original XML string
	 * is returned without any modifications.
	 *
	 * @param xmlStream The raw XML string to be formatted. Cannot be null.
	 * @return The formatted XML string with proper indentation. If an exception occurs
	 *         during formatting, the original XML string is returned.
	 */
	public static String format(final String xmlStream) {
		log.debug("formatXML");

		try {
			Source xmlInput = new StreamSource(new StringReader(xmlStream));
			StringWriter stringWriter = new StringWriter();
			StreamResult xmlOutput = new StreamResult(stringWriter);

			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			transformerFactory.setAttribute("indent-number", "4");

			Transformer transformer = transformerFactory.newTransformer(); 
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.transform(xmlInput, xmlOutput);

			return xmlOutput.getWriter().toString();
		}
		catch(TransformerException e) {
			log.error("Error in XML: " + e.getMessage() + "\n"+xmlStream, e);
		}

		return xmlStream;
	}

	/**
	 * Formats a given XML Document into a human-readable string representation.
	 * The method applies indentation and specific output properties to the XML content.
	 * If an exception occurs during the transformation process, it logs the error
	 * and returns null.
	 *
	 * @param doc The XML Document to be formatted. Must not be null.
	 * @return A formatted string representation of the XML document, or null if an error occurs during processing.
	 */
	public static String format(final Document doc) {
		try {
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			transformerFactory.setAttribute("indent-number", "4");
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-1");
			StringWriter stringWriter = new StringWriter();

			Source source = new DOMSource(doc);
			Result result = new StreamResult(stringWriter);
			transformer.transform(source, result);

			String xmlString = stringWriter.toString();
			log.info("MO Request: " + xmlString);
			return xmlString;
		}
		catch(TransformerException e) {
			log.error("Error in XML Transformation: " + e.getMessage(), e);
		}

		return null;
	}

	/**
	 * Validates whether a given XML string is well-formed.
	 * This method attempts to parse the input XML string and checks
	 * if it can successfully create a valid DOM object from it.
	 *
	 * @param xml The XML string to be validated. Must not be null.
	 *            If null or invalid, the method will return false.
	 * @return true if the XML string is well-formed and can be successfully parsed;
	 *         false otherwise, such as in the case of invalid content or parsing errors.
	 */
	public static boolean checkXml(final String xml) {
		try {
			InputStream stream = new ByteArrayInputStream(xml.getBytes());
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = dbf.newDocumentBuilder();
			Document document = docBuilder.parse(stream);
			return document != null;
		} catch (Exception ex) {
			log.warn("Exception when validating xml.", ex);
			return false;
		}
	}
}
