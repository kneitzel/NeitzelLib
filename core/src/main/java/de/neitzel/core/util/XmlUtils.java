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
 * Converter with helper functions to format XML.
 */
@Slf4j
public class  XmlUtils {

	/**
	 * DateTimeFormatter instance to format a date for XML use (xs:date type).
	 */
	public static final DateTimeFormatter XML_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/**
	 * Creates a new XML element to be used inside a document.
	 *
	 * @param doc XML Document to use.
	 * @param name Name of the Element.
	 * @return Created Element.
	 */
	public static Element createElement(final Document doc, final String name) {
		log.debug("Creating a new element  " + name);
		return doc.createElement(name);
	}

	/**
	 * Creates a new XML element with text value to be used inside a document.
	 *
	 * @param doc XML Document to use.
	 * @param name Name of the Element.
	 * @param value Value of the Text Node.
	 * @return Created Element.
	 */
	public static Element createElement(final Document doc, final String name, final String value) {
		log.debug("Creating a new element  " + name + " with value: " + value);
		Element element = doc.createElement(name);

		Node content = doc.createTextNode(value != null ? value : "");
		element.appendChild(content);

		return element;
	}

	/**
	 * Creates a new XML element with date value to be used inside a document.
	 *
	 * @param doc XML Document to use.
	 * @param name Name of the Element.
	 * @param value Date to insert into node.
	 * @return Created Element.
	 */
	public static Element createElement(final Document doc, final String name, final Date value) {
		return createElement(doc, name, XML_DATE_FORMATTER.format(value.toInstant()));
	}

	/**
	 * Creates a new XML element with integer value to be used inside a document.
	 *
	 * @param doc XML Document to use.
	 * @param name Name of the Element.
	 * @param value Value of the Integer Node.
	 * @return Created Element.
	 */
	public static Element createElement(final Document doc, final String name, final Integer value) {
		log.debug("Creating a new element  " + name + " with value: " + value);
		Element element = doc.createElement(name);

		Node content = doc.createTextNode(value != null ? ""+value : "");
		element.appendChild(content);

		return element;
	}

	/**
	 * Creates a new XML element to be used inside a document and adds it to the parent.
	 *
	 * @param doc XML Document to use.
	 * @param name Name of the Element.
	 * @param parent Parent for the Element.
	 * @return Created Element.
	 */
	public static Element createAndInsertElement(final Document doc, final String name, @NonNull final Element parent) {
		log.debug("Creating a new element  " + name + " and adding it to a parent.");
		Element element = createElement(doc, name);
		parent.appendChild(element);
		return element;
	}

	/**
	 * Creates a new XML element with text value And adds it to the parent.
	 *
	 * @param name Name of the Element.
	 * @param value Value of the Text Node.
	 * @param parent Parent for the Element.
	 * @return Created Element.
	 */
	public static Element createAndInsertElement(final Document doc, final String name, final String value, @NonNull final Element parent) {
		log.debug("Creating a new element  " + name + " with value: " + value + " and adding it to a parent.");
		Element element = createElement(doc, name, value);
		parent.appendChild(element);
		return element;
	}


	/**
	 * Formats the XML from a given String with XML content.
	 * 
	 * @param xmlStream String with XML content.
	 * @return Formated XML content or original content in case of an error.
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
	 * Formats the XML from a given XML Document.
	 *
	 * @param doc XML Document to format.
	 * @return Formated XML content.
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
	 * Checks if the given xml string is valid XML
	 * @param xml XML String.
	 * @return true if xml is valid.
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
