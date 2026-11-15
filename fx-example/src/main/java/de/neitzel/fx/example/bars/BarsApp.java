package de.neitzel.fx.example.bars;

import de.neitzel.fx.control.StatusBar;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.IOException;

/** An ordinary JavaFX editor demonstrating standalone toolbar and status content. */
public final class BarsApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        boolean fxml = getParameters().getRaw().contains("fxml");
        stage.setTitle("Standalone bars — " + (fxml ? "FXML" : "Java"));
        stage.setScene(new Scene(create(fxml), 640, 360));
        stage.show();
    }

    /**
     * Builds the same screen using Java or ordinary FXMLLoader, without MVVM.
     *
     * @param fxml whether to load the FXML version
     * @return a screen ready to place in a Scene
     * @throws IOException if the example FXML cannot be loaded
     */
    public static Parent create(boolean fxml) throws IOException {
        if (fxml) {
            FXMLLoader loader = new FXMLLoader(BarsApp.class.getResource("Bars.fxml"));
            Parent root = loader.load();
            var nodes = loader.getNamespace();
            connect((TextArea) nodes.get("editor"), (Button) nodes.get("clear"),
                    (Button) nodes.get("append"), (Label) nodes.get("status"),
                    (Label) nodes.get("cursor"), (ProgressBar) nodes.get("progress"));
            return root;
        }
        TextArea editor = new TextArea();
        editor.setId("editor");
        editor.setPromptText("Type here, or append sample text.");
        Button clear = new Button("Clear");
        clear.setId("clear");
        Button append = new Button("Append");
        append.setId("append");
        Label status = new Label();
        status.setId("status");
        Label cursor = new Label();
        cursor.setId("cursor");
        ProgressBar progress = new ProgressBar(0);
        progress.setId("progress");
        progress.setPrefWidth(120);
        StatusBar bar = new StatusBar();
        bar.getLeftItems().add(status);
        bar.getRightItems().addAll(cursor, progress);
        BorderPane root = new BorderPane(editor);
        root.setTop(new ToolBar(clear, append));
        root.setBottom(bar);
        connect(editor, clear, append, status, cursor, progress);
        return root;
    }

    private static void connect(TextArea editor, Button clear, Button append,
                                Label status, Label cursor, ProgressBar progress) {
        clear.setOnAction(event -> editor.clear());
        append.setOnAction(event -> editor.appendText("Hello"));
        status.textProperty().bind(Bindings.when(editor.textProperty().isEmpty())
                .then("Ready").otherwise("Edited"));
        cursor.textProperty().bind(editor.caretPositionProperty().asString("Offset %d"));
        progress.progressProperty().bind(Bindings.min(1.0, editor.lengthProperty().divide(100.0)));
    }
}
