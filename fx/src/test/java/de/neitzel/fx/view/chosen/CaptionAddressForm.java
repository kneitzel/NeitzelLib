package de.neitzel.fx.view.chosen;

import de.neitzel.fx.view.ViewModelClass;
import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.VBox;

/**
 * Component that names {@link StreetTitleViewModel} for the address it shows.
 */
@Singleton
@ViewModelClass(StreetTitleViewModel.class)
public class CaptionAddressForm extends VBox {

    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * Returns the model property a parent View sets with a Direct binding.
     *
     * @return the model property
     */
    public ObjectProperty<Object> modelProperty() {
        return model;
    }

    /**
     * Returns the address this component shows.
     *
     * @return the address, or {@code null} when none is set
     */
    public Object getModel() {
        return model.get();
    }

    /**
     * Sets the address this component shows.
     *
     * @param model the address
     */
    public void setModel(Object model) {
        this.model.set(model);
    }
}
