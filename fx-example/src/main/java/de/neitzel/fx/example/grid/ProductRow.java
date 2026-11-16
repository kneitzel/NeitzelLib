package de.neitzel.fx.example.grid;

import de.neitzel.fx.view.ResultRow;

import javafx.fxml.*;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.util.Map;

/** A fresh FXML row bound once to an immutable Product. */
public final class ProductRow {
    @FXML private Label id, name, category, quantity, price;

    public static ResultRow create(Product p) {
        try {
            var loader = new FXMLLoader(ProductRow.class.getResource("ProductRow.fxml"));
            HBox root = loader.load();
            ProductRow c = loader.getController();
            c.id.setText(Long.toString(p.id()));
            c.name.setText(show(p.name()));
            c.category.setText(show(p.category()));
            c.quantity.setText(show(p.quantity()));
            c.price.setText(show(p.price()));
            return new ResultRow(
                    root,
                    Map.of(
                            "id",
                            c.id,
                            "name",
                            c.name,
                            "category",
                            c.category,
                            "quantity",
                            c.quantity,
                            "price",
                            c.price),
                    () -> {});
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load product row", e);
        }
    }

    private static String show(Object v) {
        return v == null ? "(missing)" : v.toString();
    }
}
