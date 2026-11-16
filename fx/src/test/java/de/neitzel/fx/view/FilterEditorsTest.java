package de.neitzel.fx.view;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.fx.grid.filter.*;
import de.neitzel.grid.*;

import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class FilterEditorsTest {
    @BeforeAll
    static void start() throws Exception {
        DataGridTest.start();
        DataGridTest.fx(() -> javafx.application.Platform.setImplicitExit(false));
    }

    static <T extends Node> T node(FilterEditor e, String id, Class<T> type) {
        return type.cast(e.view().lookup("#" + id));
    }

    @SuppressWarnings("unchecked")
    static void choice(FilterEditor e, String id, String value) {
        ((ComboBox<String>) e.view().lookup("#" + id)).setValue(value);
    }

    static FilterEditorContext context(FilterColumn config, FilterCondition committed) {
        return new FilterEditorContext(
                "x",
                config,
                committed,
                List.of(),
                config.checklist() == FilterColumn.Checklist.INHERIT
                        ? FilterColumn.Checklist.DISABLED
                        : config.checklist());
    }

    @Test
    void realFxmlPreservesLiteralTextAndExactLocaleNumbers() throws Exception {
        DataGridTest.fx(
                () -> {
                    try (var editor =
                            BuiltInFilterEditor.load(context(FilterColumn.text(), null))) {
                        choice(editor, "mode", "Comparison");
                        node(editor, "operand", TextField.class).setText(" %_ ");
                        assertEquals(
                                new TextFilter("x", TextFilter.Operator.EQUALS, " %_ ", false),
                                editor.draft());
                        node(editor, "operand", TextField.class).setText("");
                        assertNotNull(editor.draft());
                        choice(editor, "operator", "CONTAINS");
                        assertThrows(IllegalArgumentException.class, editor::draft);
                        assertFalse(editor.validationMessageProperty().get().isBlank());
                        choice(editor, "mode", "Missing");
                        assertEquals(new MissingFilter("x", true), editor.draft());
                        choice(editor, "mode", "Clear filter");
                        assertNull(editor.draft());
                    }
                    var config =
                            FilterColumn.number(
                                    Locale.GERMANY,
                                    false,
                                    new BigDecimal("-100"),
                                    new BigDecimal("100"));
                    try (var editor =
                            BuiltInFilterEditor.load(
                                    context(
                                            config,
                                            new NumericFilter(
                                                    "x",
                                                    NumericFilter.Operator.EQUALS,
                                                    new BigDecimal("1.20"),
                                                    null)))) {
                        assertEquals("1,20", node(editor, "operand", TextField.class).getText());
                        assertEquals(
                                new BigDecimal("1.20"), ((NumericFilter) editor.draft()).operand());
                        for (String invalid :
                                List.of("NaN", "Infinity", "1.234,50", "101", "bad")) {
                            node(editor, "operand", TextField.class).setText(invalid);
                            assertThrows(IllegalArgumentException.class, editor::draft);
                        }
                        node(editor, "operand", TextField.class).setText("2");
                        choice(editor, "operator", "RANGE");
                        node(editor, "upper", TextField.class).setText("1");
                        assertThrows(IllegalArgumentException.class, editor::draft);
                    }
                    try (var editor =
                            BuiltInFilterEditor.load(
                                    context(
                                            FilterColumn.number(
                                                    Locale.US,
                                                    true,
                                                    BigDecimal.ZERO,
                                                    BigDecimal.valueOf(Integer.MAX_VALUE)),
                                            null))) {
                        choice(editor, "mode", "Comparison");
                        for (String invalid : List.of("1.5", "2147483648", "9223372036854775808")) {
                            node(editor, "operand", TextField.class).setText(invalid);
                            assertThrows(IllegalArgumentException.class, editor::draft);
                        }
                    }
                });
    }

    @Test
    void lookupRacesFailuresAndSearchNeverEraseOffBatchSelections() throws Exception {
        var pending = new ArrayList<CompletableFuture<FilterCandidates.Batch>>();
        AtomicInteger disposed = new AtomicInteger();
        FilterCandidates.Provider provider =
                new FilterCandidates.Provider() {
                    public Set<FilterCandidates.Scope> scopes() {
                        return Set.of(FilterCandidates.Scope.CURRENT_PAGE);
                    }

                    public FilterCandidates.Session open(FilterCandidates.Context c) {
                        return new FilterCandidates.Session() {
                            public CompletionStage<FilterCandidates.Batch> load(
                                    FilterCandidates.Request r) {
                                var f = new CompletableFuture<FilterCandidates.Batch>();
                                pending.add(f);
                                return f;
                            }

                            public void close() {
                                disposed.incrementAndGet();
                            }
                        };
                    }
                };
        var ref = new AtomicReference<BuiltInFilterEditor>();
        DataGridTest.fx(
                () -> {
                    ref.set(
                            BuiltInFilterEditor.load(
                                    context(
                                            FilterColumn.text()
                                                    .withChecklist(
                                                            FilterColumn.Checklist.CURRENT_PAGE,
                                                            provider),
                                            new ValueFilter(
                                                    "x",
                                                    Set.of(new FilterValue.Text("off-page")),
                                                    true))));
                    node(ref.get(), "search", TextField.class).setText("b");
                    pending.get(1)
                            .complete(
                                    new FilterCandidates.Batch(
                                            List.of(
                                                    new FilterCandidates.Candidate(
                                                            new FilterValue.Text("b"), "B")),
                                            false));
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    pending.getFirst()
                            .complete(
                                    new FilterCandidates.Batch(
                                            List.of(
                                                    new FilterCandidates.Candidate(
                                                            new FilterValue.Text("old"), "Old")),
                                            false));
                    node(ref.get(), "selectShown", Button.class).fire();
                    assertEquals(
                            Set.of(new FilterValue.Text("off-page"), new FilterValue.Text("b")),
                            ((ValueFilter) ref.get().draft()).values());
                    node(ref.get(), "search", TextField.class).setText("fail");
                    pending.getLast().completeExceptionally(new IllegalStateException("offline"));
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertTrue(
                            node(ref.get(), "lookupMessage", Label.class)
                                    .getText()
                                    .contains("offline"));
                    assertEquals(2, ((ValueFilter) ref.get().draft()).values().size());
                    node(ref.get(), "retry", Button.class).fire();
                    ref.get().close();
                    pending.getLast().complete(new FilterCandidates.Batch(List.of(), false));
                    assertEquals(1, disposed.get());
                });
        DataGridTest.drain();
    }

    @Test
    void configurationAndCapabilitiesFailVisiblyWithoutFallback() throws Exception {
        DataGridTest.fx(
                () -> {
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    FilterColumn.text()
                                            .withChecklist(
                                                    FilterColumn.Checklist.CURRENT_PAGE, null));
                    var config =
                            FilterColumn.text()
                                    .withChecklist(
                                            FilterColumn.Checklist.SOURCE_WIDE,
                                            FilterCandidates.currentPage(List::of));
                    try (var e = BuiltInFilterEditor.load(context(config, null))) {
                        choice(e, "mode", "Checklist");
                        assertThrows(IllegalArgumentException.class, e::draft);
                        assertTrue(node(e, "scope", Label.class).getText().contains("unavailable"));
                        choice(e, "mode", "Comparison");
                        assertNotNull(e.draft());
                    }
                    var registry = FilterEditors.builtIns();
                    assertThrows(IllegalArgumentException.class, () -> registry.require("absent"));
                    assertThrows(
                            UnsupportedOperationException.class,
                            () ->
                                    registry.require("neitzel.text")
                                            .requireEvaluation("jdbc-unknown"));
                    assertTrue(registry.require("neitzel.text").capabilities().persistence());
                    assertThrows(
                            UnsupportedOperationException.class,
                            () ->
                                    registry.require("neitzel.text")
                                            .validate(
                                                    new NumericFilter(
                                                            "x",
                                                            NumericFilter.Operator.EQUALS,
                                                            BigDecimal.ONE,
                                                            null)));
                });
    }

    @Test
    void popupDraftValidationDismissalNestedEscapeAndOwnerCleanup() throws Exception {
        AtomicReference<FilterCondition> committed =
                new AtomicReference<>(
                        new TextFilter("x", TextFilter.Operator.EQUALS, "old", false));
        DataGridTest.fx(
                () -> {
                    Stage stage = new Stage();
                    Button opener = new Button("Filter");
                    stage.setScene(new Scene(new VBox(opener), 500, 400));
                    stage.show();
                    try {
                        var editor =
                                BuiltInFilterEditor.load(
                                        context(FilterColumn.text(), committed.get()));
                        var popup =
                                new FilterPopup(
                                        opener,
                                        editor,
                                        new FilterPopup.Options(
                                                FilterPopup.Outside.ACCEPT, false, false),
                                        c -> {
                                            if (c instanceof TextFilter f
                                                    && f.operand().equals("unsupported"))
                                                throw new UnsupportedOperationException(
                                                        "Unsupported source semantics");
                                        },
                                        committed::set);
                        popup.show();
                        node(editor, "operand", TextField.class).setText("unsupported");
                        popup.outside();
                        assertTrue(popup.isOpen());
                        assertTrue(popup.errorMessage().contains("Unsupported"));
                        assertEquals("old", ((TextFilter) committed.get()).operand());
                        node(editor, "operand", TextField.class).setText("new");
                        ComboBox<?> chooser = node(editor, "operator", ComboBox.class);
                        chooser.show();
                        javafx.event.Event.fireEvent(
                                editor.view(),
                                new KeyEvent(
                                        KeyEvent.KEY_PRESSED,
                                        "",
                                        "",
                                        KeyCode.ESCAPE,
                                        false,
                                        false,
                                        false,
                                        false));
                        assertFalse(chooser.isShowing());
                        assertTrue(popup.isOpen());
                        javafx.event.Event.fireEvent(
                                editor.view(),
                                new KeyEvent(
                                        KeyEvent.KEY_PRESSED,
                                        "",
                                        "",
                                        KeyCode.ENTER,
                                        false,
                                        false,
                                        false,
                                        false));
                        assertFalse(popup.isOpen());
                        assertEquals("new", ((TextFilter) committed.get()).operand());
                        var second =
                                BuiltInFilterEditor.load(
                                        context(FilterColumn.text(), committed.get()));
                        var cancel =
                                new FilterPopup(
                                        opener,
                                        second,
                                        FilterPopup.Options.defaults(),
                                        c -> {},
                                        committed::set);
                        cancel.show();
                        node(second, "operand", TextField.class).setText("discard");
                        cancel.outside();
                        assertEquals("new", ((TextFilter) committed.get()).operand());
                        var third =
                                BuiltInFilterEditor.load(
                                        context(FilterColumn.text(), committed.get()));
                        var disposed =
                                new FilterPopup(
                                        opener,
                                        third,
                                        FilterPopup.Options.defaults(),
                                        c -> {},
                                        committed::set);
                        disposed.show();
                        stage.close();
                        assertFalse(disposed.isOpen());
                        assertThrows(IllegalStateException.class, third::draft);
                    } finally {
                        stage.close();
                    }
                });
        DataGridTest.drain();
    }

    public record Prefix(String columnId, String operand) implements FilterCondition {
        public String typeId() {
            return "test.prefix";
        }
    }

    public static final class CustomEditor implements FilterEditor {
        @javafx.fxml.FXML private TextField value;
        private Parent root;
        private String column;
        private final javafx.beans.property.ReadOnlyStringWrapper message =
                new javafx.beans.property.ReadOnlyStringWrapper("");

        static CustomEditor load(FilterEditorContext context) {
            try {
                var loader =
                        new javafx.fxml.FXMLLoader(
                                FilterEditorsTest.class.getResource("CustomFilterEditor.fxml"));
                var editor = new CustomEditor();
                loader.setController(editor);
                editor.root = loader.load();
                editor.column = context.columnId();
                editor.value.setText(context.committed() instanceof Prefix f ? f.operand() : "a");
                return editor;
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(failure);
            }
        }

        public Parent view() {
            return root;
        }

        public javafx.beans.property.ReadOnlyStringProperty validationMessageProperty() {
            return message.getReadOnlyProperty();
        }

        public FilterCondition draft() {
            return new Prefix(column, value.getText());
        }

        public void close() {}
    }

    @Test
    void customFxmlRegistrationAndHeaderActionRespectActualSourceCapabilities() throws Exception {
        var ref = new AtomicReference<DataGrid<Long, String>>();
        var window = new AtomicReference<Stage>();
        DataGridTest.fx(
                () -> {
                    var source =
                            new MemoryGridSource<Long, String>(
                                    () -> List.of("alpha", "beta"),
                                    s -> (long) s.length(),
                                    Long::compare,
                                    List.of(
                                            new MemoryColumn<>(
                                                    "text",
                                                    MemoryColumn.Kind.TEXT,
                                                    FilterValue.Text::new,
                                                    Map.of(
                                                            "test.prefix",
                                                            c ->
                                                                    v ->
                                                                            v
                                                                                            instanceof
                                                                                            FilterValue
                                                                                                            .Text
                                                                                                    t
                                                                                    && t.value()
                                                                                            .startsWith(
                                                                                                    ((Prefix)
                                                                                                                    c)
                                                                                                            .operand())))),
                                    Runnable::run,
                                    50);
                    var grid =
                            new DataGrid<Long, String>(
                                    List.of(new ResultColumn("text", "Text", 200, 50, 300, -1)),
                                    value -> {
                                        var label = new Label(value);
                                        return new ResultRow(
                                                new HBox(label), Map.of("text", label), () -> {});
                                    },
                                    source);
                    var registry = new FilterEditors();
                    registry.register(
                            new FilterEditors.Registration(
                                    "test.prefix",
                                    new FilterCapabilities(true, false, Set.of()),
                                    Set.of("test.prefix"),
                                    CustomEditor::load));
                    grid.setFilterEditors(registry);
                    grid.configureFilter(
                            "text",
                            new FilterColumn(
                                    "test.prefix",
                                    Locale.US,
                                    false,
                                    false,
                                    null,
                                    null,
                                    FilterColumn.Checklist.DISABLED,
                                    null));
                    Stage stage = new Stage();
                    stage.setScene(new Scene(grid, 500, 400));
                    stage.show();
                    window.set(stage);
                    ref.set(grid);
                    grid.load();
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    var grid = ref.get();
                    var before = grid.stateProperty().get().generation();
                    ((Button) grid.lookup(".filter-button")).fire();
                    assertEquals(
                            before,
                            grid.stateProperty().get().generation()); // Opener must not sort.
                    Parent popup =
                            javafx.stage.Window.getWindows().stream()
                                    .map(w -> w.getScene().getRoot())
                                    .filter(root -> root.lookup("#filter-ok") != null)
                                    .findFirst()
                                    .orElseThrow();
                    ((Button) popup.lookup("#filter-ok")).fire();
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    var grid = ref.get();
                    assertEquals(
                            List.of(new Prefix("text", "a")),
                            grid.stateProperty().get().displayed().filters());
                    assertEquals(
                            "alpha", grid.stateProperty().get().result().rows().getFirst().model());
                    long generation = grid.stateProperty().get().generation();
                    assertThrows(
                            UnsupportedOperationException.class,
                            () ->
                                    grid.setFilters(
                                            List.of(
                                                    new FilterCondition() {
                                                        public String columnId() {
                                                            return "text";
                                                        }

                                                        public String typeId() {
                                                            return "unknown";
                                                        }
                                                    })));
                    assertEquals(generation, grid.stateProperty().get().generation());
                    grid.close();
                    window.get().close();
                });
    }
}
