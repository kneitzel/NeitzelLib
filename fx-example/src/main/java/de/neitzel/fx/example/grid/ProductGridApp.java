package de.neitzel.fx.example.grid;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;

/** Standalone entry point; resources are application-owned and released on shutdown. */
public final class ProductGridApp extends Application {
    private ProductCatalog catalog;
    private ProductGridDemo demo;

    public void init() throws Exception {
        catalog = new ProductCatalog(10000);
    }

    public void start(Stage stage) {
        demo =
                new ProductGridDemo(
                        catalog,
                        Path.of(
                                System.getProperty("user.home"),
                                ".neitzellib-example",
                                "products-setup.json"));
        var scene = new Scene(demo, 1120, 820);
        demo.install(scene);
        stage.setScene(scene);
        stage.setTitle("DataGrid — Memory and H2");
        stage.show();
    }

    public void stop() {
        DemoResources.closeAll(demo, catalog);
    }
}
