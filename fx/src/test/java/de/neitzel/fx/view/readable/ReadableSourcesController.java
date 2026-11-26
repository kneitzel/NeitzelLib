package de.neitzel.fx.view.readable;

import de.neitzel.fx.view.Converter;
import de.neitzel.fx.view.ViewModel;
import de.neitzel.fx.view.ViewModelClass;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

/** Controller and presentation state for the read-only DirectBinding fixture. */

@ViewModelClass(ReadableSourcesController.Sources.class)
public class ReadableSourcesController {

    /** Sources include a JavaFX binding, a read-only property, and an observable-typed accessor. */
    public static class Sources extends ViewModel {
        @Getter
        private final ObservableList<String> choices = FXCollections.observableArrayList();
        @Getter
        private int evaluations;
        private final BooleanBinding disabled = Bindings.createBooleanBinding(() -> {
            evaluations++;
            return choices.isEmpty();
        }, choices);
        private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper("First");

        public Sources(Object model) {
            super(model);
        }

        public BooleanBinding disabledProperty() {
            return disabled;
        }

        public ReadOnlyStringProperty titleProperty() {
            return title.getReadOnlyProperty();
        }

        public ObservableValue<String> observableTitleProperty() {
            return title;
        }

        public void setTitle(String value) {
            title.set(value);
        }
    }

    /** A display-only converter, with an invalid title for testing Message reporting. */

    public static class Caption implements Converter {
        @Override
        public Object toControlValue(Object value) {
            if ("".equals(value)) {
                throw new IllegalArgumentException("Caption is required");
            }
            return "Caption: " + value;
        }
    }
}
