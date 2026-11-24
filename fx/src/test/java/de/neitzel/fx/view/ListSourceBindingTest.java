package de.neitzel.fx.view;

import de.neitzel.fx.control.AutoCompleteTextField;
import de.neitzel.injection.ApplicationContext;
import java.util.Map;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ListSourceBindingTest {
    public static class Tags {
        private final ListProperty<String> tags =
                new SimpleListProperty<>(FXCollections.observableArrayList("First"));
        public ListProperty<String> tagsProperty() { return tags; }
    }

    @BeforeAll static void toolkit() throws Exception { DataGridTest.start(); }

    @Test void viewBindingTracksEqualListReplacementAndRestoresThePreviousItems() throws Exception {
        DataGridTest.fx(() -> {
            var model = new Tags();
            var combo = new ComboBox<String>();
            var previous = combo.getItems();
            var binding = new DirectBinding();
            binding.setControl("combo");
            binding.setProperty("items");
            binding.setPath("tags");
            binding.setDirection("view");
            try (var models = new ViewModelContext(); var lifetime = new ViewLifetime(models)) {
                DirectBindingApplier.apply(Map.of("combo", combo), model, binding, new ApplicationContext(Tags.class), lifetime);
                assertSame(model.tags.get(), combo.getItems());
                var replacement = FXCollections.observableArrayList("First");
                model.tags.set(replacement);
                assertSame(replacement, combo.getItems());
                model.tags.set(null);
                assertNull(combo.getItems());
            }
            assertSame(previous, combo.getItems());
            model.tags.set(FXCollections.observableArrayList("Detached"));
            assertSame(previous, combo.getItems());
        });
    }

    @Test void ordinaryJavaFxBindingReturnsToFixedSuggestionsOnUnbind() throws Exception {
        DataGridTest.fx(() -> {
            var field = new AutoCompleteTextField();
            field.getSuggestions().add("Fixed");
            var model = new Tags();
            field.suggestionsSourceProperty().bind(model.tags);
            assertSame(model.tags.get(), field.getSuggestionsSource());
            field.suggestionsSourceProperty().unbind();
            assertNull(field.getSuggestionsSource());
            model.tags.set(FXCollections.observableArrayList("Detached"));
            assertNull(field.getSuggestionsSource());
            assertEquals(java.util.List.of("Fixed"), field.getSuggestions());
        });
    }
}
