package de.neitzel.fx.example.theme;

import de.neitzel.fx.grid.filter.*;
import de.neitzel.fx.theme.*;
import de.neitzel.fx.view.*;
import de.neitzel.grid.*;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.util.*;

/** Runnable state gallery; no stored preference, source credentials or application data. */
public final class ThemeGallery extends VBox implements AutoCloseable {
    public final ThemeManager themes = new ThemeManager();
    public final Button normal = new Button("Normal"),
            hovered = new Button("Hovered"),
            focused = new Button("Focused"),
            disabled = new Button("Disabled");
    public final TextField invalid = new TextField("invalid input"),
            draft = new TextField("An unfinished edit survives theme changes");
    public final Label error = new Label("! Invalid value — check the input"),
            warning = new Label("! Warning — review this value");
    public final TableView<String> standard = new TableView<>();
    public final DataGrid<Integer, String> grid;
    private final List<Stage> floating = new ArrayList<>();

    public ThemeGallery() {
        setSpacing(12);
        setStyle("-fx-padding: 18;");
        getStyleClass().add("theme-gallery");
        var picker = new ComboBox<String>();
        picker.getItems().setAll("light", "dark", "high-contrast");
        picker.setValue("light");
        picker.setOnAction(
                e -> {
                    if (!picker.getValue().equals(themes.selectedThemeProperty().get().id()))
                        themes.select(picker.getValue());
                });
        themes.selectedThemeProperty().addListener((o, a, b) -> picker.setValue(b.id()));
        var failure = new Label();
        failure.setWrapText(true);
        failure.getStyleClass().add("theme-error");
        themes.failureProperty()
                .addListener((o, a, b) -> failure.setText(b == null ? "" : b.message()));
        Label title = new Label("Shared theme gallery");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        hovered.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
        focused.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
        disabled.setDisable(true);
        invalid.pseudoClassStateChanged(PseudoClass.getPseudoClass("invalid"), true);
        invalid.setAccessibleText("Invalid value; check the input");
        error.getStyleClass().add("theme-error");
        warning.getStyleClass().add("theme-warning");
        CheckBox checked = new CheckBox("Checked");
        checked.setSelected(true);
        RadioButton radio = new RadioButton("Selected radio");
        radio.setSelected(true);
        DatePicker date = new DatePicker();
        date.setPromptText("Nested date chooser");
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll("Choose a value", "Alpha", "Beta");
        combo.getSelectionModel().selectFirst();
        TableColumn<String, String> state =
                new TableColumn<>("Record indicator (text survives selection)");
        state.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue()));
        state.setPrefWidth(380);
        standard.getColumns().add(state);
        standard.getItems()
                .setAll(
                        "FILE — from saved input",
                        "LIVE — observed record",
                        "REMOVE — pending removal");
        standard.getSelectionModel().select(2);
        standard.setPrefHeight(135);
        var source =
                new MemoryGridSource<Integer, String>(
                        () -> List.of("Alpha", "Beta", "Gamma"),
                        value -> List.of("Alpha", "Beta", "Gamma").indexOf(value),
                        Integer::compare,
                        List.of(
                                new MemoryColumn<>(
                                        "name", MemoryColumn.Kind.TEXT, FilterValue.Text::new)),
                        Runnable::run,
                        100);
        grid =
                new DataGrid<>(
                        List.of(new ResultColumn("name", "Name", 430, 160, 600, -1)),
                        value -> {
                            Label label = new Label(value);
                            label.setPadding(new javafx.geometry.Insets(6));
                            return new ResultRow(new HBox(label), Map.of("name", label), () -> {});
                        },
                        source);
        grid.configureFilter(
                "name",
                FilterColumn.text()
                        .withChecklist(
                                FilterColumn.Checklist.SOURCE_WIDE,
                                source.candidateProvider(
                                        value -> ((FilterValue.Text) value).value())));
        grid.setFilters(List.of(new TextFilter("name", TextFilter.Operator.CONTAINS, "a", false)));
        grid.select(0);
        grid.setPrefHeight(210);
        Button dialog = new Button("Open dialog"),
                floatWindow = new Button("Open floating window"),
                loading = new Button("Toggle loading sample");
        dialog.setOnAction(
                e -> {
                    Alert alert =
                            new Alert(
                                    Alert.AlertType.INFORMATION,
                                    "Dialogs inherit the selected theme, including newly opened"
                                            + " windows.",
                                    ButtonType.OK);
                    alert.initOwner(getScene().getWindow());
                    alert.show();
                });
        floatWindow.setOnAction(
                e -> {
                    Stage stage = new Stage();
                    stage.initOwner(getScene().getWindow());
                    stage.setTitle("Themed floating window");
                    var field =
                            new TextArea("Edit this text, then change the theme in the gallery.");
                    stage.setScene(
                            new Scene(new VBox(12, new Label("Floating editor"), field), 420, 200));
                    floating.add(stage);
                    stage.show();
                });
        Label loadingStatus = new Label("Loading… retained rows are unavailable");
        loadingStatus.getStyleClass().add("data-grid-status");
        HBox loadingState = new HBox(10, new ProgressIndicator(), loadingStatus);
        loadingState.getStyleClass().add("data-grid");
        loadingState.pseudoClassStateChanged(PseudoClass.getPseudoClass("loading"), true);
        ((ProgressIndicator) loadingState.getChildren().getFirst()).setPrefSize(22, 22);
        loading.setOnAction(e -> loadingState.setVisible(!loadingState.isVisible()));
        getChildren()
                .addAll(
                        new HBox(16, title, picker),
                        new Label(
                                "Explicit state specimens: hover / focus are CSS states, not a"
                                        + " native input test."),
                        new HBox(10, normal, hovered, focused, disabled),
                        draft,
                        new HBox(10, checked, radio, combo, date),
                        invalid,
                        error,
                        warning,
                        standard,
                        grid,
                        loadingState,
                        new HBox(10, dialog, floatWindow, loading),
                        failure);
    }

    public void install(Scene scene) {
        themes.register(scene);
    }

    public void close() {
        grid.close();
        for (var stage : floating) stage.close();
        themes.close();
    }
}
