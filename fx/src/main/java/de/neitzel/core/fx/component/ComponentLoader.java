package de.neitzel.core.fx.component;

import javafx.beans.property.Property;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ComponentLoader is responsible for loading JavaFX FXML components and binding
 * them to automatically generated ViewModels based on simple POJO models.
 * <p>
 * It parses custom NFX attributes in FXML to bind UI elements to properties in the ViewModel,
 * and supports recursive loading of subcomponents.
 */
public class ComponentLoader {
    private Map<String, Map<String, String>> nfxBindingMap = new HashMap<>();

    /**
     * Loads an FXML file and binds its elements to a generated ViewModel
     * based on the given POJO model.
     *
     * @param model    the data model (POJO) to bind to the UI
     * @param fxmlPath the path to the FXML file
     * @return the root JavaFX node loaded from FXML
     */
    public Parent load(Object model, String fxmlPath) {
        try {
            AutoViewModel<?> viewModel = new AutoViewModel<>(model);
            String cleanedUri = preprocessNfxAttributes(fxmlPath);
            FXMLLoader loader = new FXMLLoader(new URL(cleanedUri));
            loader.setControllerFactory(type -> new ComponentController(viewModel));
            Parent root = loader.load();
            processNfxBindings(root, viewModel, loader);
            return root;
        } catch (IOException e) {
            throw new RuntimeException("unable to load fxml: " + fxmlPath, e);
        }
    }

    /**
     * Processes all UI elements for NFX binding attributes and applies
     * the appropriate bindings to the ViewModel.
     *
     * @param root      the root node of the loaded FXML hierarchy
     * @param viewModel the generated ViewModel to bind against
     */
    private void processNfxBindings(Parent root, AutoViewModel<?> viewModel, FXMLLoader loader) {
        walkNodes(root, node -> {
            var nfx = lookupNfxAttributes(node, loader);
            String target = nfx.get("nfx:target");
            String direction = nfx.get("nfx:direction");
            String source = nfx.get("nfx:source");

            if (target != null && direction != null) {
                Property<?> vmProp = viewModel.getProperty(target);
                bindNodeToProperty(node, vmProp, direction);
            }

            if (source != null) {
                Object subModel = ((Property<?>) viewModel.getProperty(target)).getValue();
                Parent subComponent = load(subModel, source);
                if (node instanceof Pane pane) {
                    pane.getChildren().setAll(subComponent);
                }
            }
        });
    }

    /**
     * Recursively walks all nodes in the scene graph starting from the root,
     * applying the given consumer to each node.
     *
     * @param root     the starting node
     * @param consumer the consumer to apply to each node
     */
    private void walkNodes(Parent root, java.util.function.Consumer<javafx.scene.Node> consumer) {
        consumer.accept(root);
        if (root instanceof Pane pane) {
            for (javafx.scene.Node child : pane.getChildren()) {
                if (child instanceof Parent p) {
                    walkNodes(p, consumer);
                } else {
                    consumer.accept(child);
                }
            }
        }
    }

    /**
     * Extracts custom NFX attributes from a node's properties map.
     * These attributes are expected to be in the format "nfx:..." and hold string values.
     *
     * @param node the node to inspect
     * @return a map of NFX attribute names to values
     */
    private Map<String, String> extractNfxAttributes(javafx.scene.Node node) {
        Map<String, String> result = new HashMap<>();
        node.getProperties().forEach((k, v) -> {
            if (k instanceof String key && key.startsWith("nfx:") && v instanceof String value) {
                result.put(key, value);
            }
        });
        return result;
    }

    /**
     * Binds a JavaFX UI control to a ViewModel property according to the specified direction.
     *
     * @param node      the UI node (e.g., TextField)
     * @param vmProp    the ViewModel property to bind to
     * @param direction the direction of the binding (e.g., "bidirectional", "read")
     */
    private void bindNodeToProperty(javafx.scene.Node node, Property<?> vmProp, String direction) {
        if (node instanceof javafx.scene.control.TextField tf && vmProp instanceof javafx.beans.property.StringProperty sp) {
            if ("bidirectional".equalsIgnoreCase(direction)) {
                tf.textProperty().bindBidirectional(sp);
            } else if ("read".equalsIgnoreCase(direction)) {
                tf.textProperty().bind(sp);
            }
        }
        // Additional control types (e.g., CheckBox, ComboBox) can be added here
    }

    private String preprocessNfxAttributes(String fxmlPath) {
        try {
            nfxBindingMap.clear();
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            var builder = factory.newDocumentBuilder();
            var doc = builder.parse(getClass().getResourceAsStream(fxmlPath));
            var all = doc.getElementsByTagName("*");

            int autoId = 0;
            for (int i = 0; i < all.getLength(); i++) {
                var el = (org.w3c.dom.Element) all.item(i);
                Map<String, String> nfxAttrs = new HashMap<>();
                var attrs = el.getAttributes();

                List<String> toRemove = new ArrayList<>();
                for (int j = 0; j < attrs.getLength(); j++) {
                    var attr = (org.w3c.dom.Attr) attrs.item(j);
                    if (attr.getName().startsWith("nfx:")) {
                        nfxAttrs.put(attr.getName(), attr.getValue());
                        toRemove.add(attr.getName());
                    }
                }

                if (!nfxAttrs.isEmpty()) {
                    String fxid = el.getAttribute("fx:id");
                    if (fxid == null || fxid.isBlank()) {
                        fxid = "auto_id_" + (++autoId);
                        el.setAttribute("fx:id", fxid);
                    }
                    nfxBindingMap.put(fxid, nfxAttrs);
                    toRemove.forEach(el::removeAttribute);
                }
            }

            // Speichere das bereinigte Dokument als temporäre Datei
            File tempFile = File.createTempFile("cleaned_fxml", ".fxml");
            tempFile.deleteOnExit();
            var transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.transform(new javax.xml.transform.dom.DOMSource(doc),
                                  new javax.xml.transform.stream.StreamResult(tempFile));

            return tempFile.toURI().toString();
        } catch (Exception e) {
            throw new RuntimeException("Preprocessing failed for: " + fxmlPath, e);
        }
    }

    private Map<String, String> lookupNfxAttributes(javafx.scene.Node node, FXMLLoader loader) {
        String fxid = loader.getNamespace().entrySet().stream()
            .filter(e -> e.getValue() == node)
            .map(Map.Entry::getKey)
            .findFirst().orElse(null);
        if (fxid == null) return Map.of();
        return nfxBindingMap.getOrDefault(fxid, Map.of());
    }
}
