package de.neitzel.fx.view.component;

import de.neitzel.fx.view.fixture.Visit;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

/**
 * A Row that shows a visit's street and city, street first.
 */
@Singleton
public class VisitRow extends HBox {

    /**
     * The visit this row shows.
     */
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * The street field, bound by this row.
     */
    @FXML
    private TextField streetField;

    /**
     * Creates a row.
     */
    @Inject
    public VisitRow() {
        model.addListener((observable, previous, current) -> applyHeight(current));
    }

    /**
     * Gives a visit named Tall a taller row. Every other visit stays one line.
     *
     * @param current the model, or {@code null}
     */
    private void applyHeight(Object current) {
        if (current instanceof Visit visit && "Tall".equals(visit.getStreet())) {
            setPrefHeight(80);
        } else {
            setPrefHeight(24);
        }
    }

    /**
     * Returns the model property.
     *
     * @return the model property
     */
    public ObjectProperty<Object> modelProperty() {
        return model;
    }

    /**
     * Returns the visit this row shows.
     *
     * @return the visit, or {@code null}
     */
    public Object getModel() {
        return model.get();
    }

    /**
     * Sets the visit this row shows.
     *
     * @param model the visit
     */
    public void setModel(Object model) {
        this.model.set(model);
    }
}
