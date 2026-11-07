package de.neitzel.fx.view;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Reads the Controller class named by an FXML document.
 */
final class FxmlControllers {

    /**
     * The FXML namespace used by {@code fx:controller}.
     */
    private static final String FXML_NAMESPACE = "http://javafx.com/fxml";

    /**
     * Prevents instantiation.
     */
    private FxmlControllers() {
    }

    /**
     * Returns the Controller class named on the document root, or {@code null} when the document
     * names none.
     *
     * @param fxml the FXML document
     * @return the Controller class, or {@code null}
     */
    static Class<?> controllerClass(URL fxml) {
        String name = controllerName(fxml);
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException failure) {
            throw new IllegalArgumentException("No controller " + name, failure);
        }
    }

    /**
     * Returns the {@code fx:controller} value on the document root.
     *
     * @param fxml the FXML document
     * @return the class name, or {@code null} when the document names none
     */
    private static String controllerName(URL fxml) {
        try (InputStream input = fxml.openStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Element root = factory.newDocumentBuilder().parse(input).getDocumentElement();
            String named = root.getAttributeNS(FXML_NAMESPACE, "controller");
            if (named != null && !named.isEmpty()) {
                return named;
            }
            NamedNodeMap attributes = root.getAttributes();
            for (int index = 0; index < attributes.getLength(); index++) {
                Node attribute = attributes.item(index);
                if ("controller".equals(attribute.getLocalName())
                        || "fx:controller".equals(attribute.getNodeName())) {
                    return attribute.getNodeValue();
                }
            }
            return null;
        } catch (IOException | ParserConfigurationException | SAXException failure) {
            throw new IllegalStateException("Could not read controller from " + fxml, failure);
        }
    }
}
