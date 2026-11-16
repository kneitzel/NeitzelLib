package de.neitzel.fx.example.theme;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class ThemeGalleryApp extends Application {
    private ThemeGallery gallery;

    public void start(Stage stage) {
        gallery = new ThemeGallery();
        Scene scene = new Scene(gallery, 920, 900);
        gallery.install(scene);
        stage.setTitle("NeitzelLib theme gallery");
        stage.setScene(scene);
        stage.show();
    }

    public void stop() {
        if (gallery != null) gallery.close();
    }
}
