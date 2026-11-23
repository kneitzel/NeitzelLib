package de.neitzel.fx.view;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javafx.beans.property.Property;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.fxml.LoadListener;

/** Removes handler properties assigned by the managed FXML document when its View closes. */
final class FxmlHandlers implements LoadListener {

    private final ViewLifetime lifetime;
    private final Deque<List<String>> handlers = new ArrayDeque<>();

    private FxmlHandlers(ViewLifetime lifetime) {
        this.lifetime = lifetime;
    }

    static void observe(FXMLLoader loader, ViewLifetime lifetime) {
        if (lifetime.managed()) loader.setLoadListener(new FxmlHandlers(lifetime));
    }

    @Override
    public void readEventHandlerAttribute(String name, String value) {
        handlers.getLast().add(name);
    }

    @Override
    public void endElement(Object value) {
        List<String> attributes = handlers.removeLast();
        if (value == null) return;
        for (String attribute : attributes) {
            Property<?> property = NamedProperties.get(value, attribute);
            if (property != null && property.getValue() instanceof EventHandler<?> handler) {
                lifetime.own(() -> {
                    if (property.getValue() == handler) property.setValue(null);
                });
            }
        }
    }

    private void beginElement() {
        handlers.addLast(new ArrayList<>());
    }

    @Override public void beginInstanceDeclarationElement(Class<?> type) { beginElement(); }
    @Override public void beginUnknownTypeElement(String name) { beginElement(); }
    @Override public void beginIncludeElement() { beginElement(); }
    @Override public void beginReferenceElement() { beginElement(); }
    @Override public void beginCopyElement() { beginElement(); }
    @Override public void beginRootElement() { beginElement(); }
    @Override public void beginPropertyElement(String name, Class<?> sourceType) { beginElement(); }
    @Override public void beginUnknownStaticPropertyElement(String name) { beginElement(); }
    @Override public void beginScriptElement() { beginElement(); }
    @Override public void beginDefineElement() { beginElement(); }

    @Override public void readImportProcessingInstruction(String target) { }
    @Override public void readLanguageProcessingInstruction(String language) { }
    @Override public void readComment(String comment) { }
    @Override public void readInternalAttribute(String name, String value) { }
    @Override public void readPropertyAttribute(String name, Class<?> sourceType, String value) { }
    @Override public void readUnknownStaticPropertyAttribute(String name, String value) { }
}
