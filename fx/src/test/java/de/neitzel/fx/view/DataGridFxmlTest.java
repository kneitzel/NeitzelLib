package de.neitzel.fx.view;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.fx.control.NonNegativeIntegerSpinner;
import de.neitzel.fx.control.StatusBar;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.grid.*;
import de.neitzel.injection.ApplicationContext;

import javafx.collections.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;

import lombok.Getter;
import lombok.Setter;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class DataGridFxmlTest {
    public record Item(long id, String text, int count) {}

    @Getter
    @Setter
    public static class Model {
        private List<Item> rows = new ArrayList<>();
        private int quantity = 10;
    }

    @TempDir Path directory;

    @BeforeAll
    static void toolkit() throws Exception {
        DataGridTest.start();
    }

    private static final String GRID =
            """
            <?import de.neitzel.fx.view.*?>
            <DataGrid xmlns:fx="http://javafx.com/fxml/1" keyAttribute="id" pageSize="1"
                      groupingEnabled="true" groupBy="text" setupId="sample" setupVersion="1">
                <columns>
                    <DataGridColumn id="text" header="Text" width="180" checklist="SOURCE_WIDE"/>
                    <DataGridColumn id="count" header="Count" kind="INTEGER"/>
                </columns>
            </DataGrid>
            """;

    @Test
    void loadsReusableTagAndCompletionControls() throws Exception {
        DataGridTest.fx(() -> {
            String xml = """
                    <?import de.neitzel.fx.control.*?>
                    <?import java.lang.String?>
                    <?import javafx.scene.layout.VBox?>
                    <VBox xmlns:fx="http://javafx.com/fxml/1">
                        <AutoCompleteTextField promptText="Tag">
                            <suggestions><String fx:value="Archiv"/></suggestions>
                        </AutoCompleteTextField>
                        <TagChip text="Archiv" removable="true"/>
                    </VBox>""";
            try {
                javafx.scene.layout.VBox box = new FXMLLoader().load(
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
                var field = (de.neitzel.fx.control.AutoCompleteTextField) box.getChildren().getFirst();
                assertEquals(List.of("Archiv"), field.getSuggestions());
                var tag = (de.neitzel.fx.control.TagChip) box.getChildren().getLast();
                assertEquals("Archiv", tag.getText());
                var remove = (javafx.scene.control.Button) tag.getChildren().getLast();
                var clicked = new AtomicBoolean();
                tag.setOnRemove(event -> clicked.set(true));
                remove.fire();
                assertTrue(clicked.get());
                tag.setRemovable(false);
                assertFalse(remove.isVisible());
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });
    }

    @Test
    void dismissibleToolbarRetainsItsItemsWhenReopened() throws Exception {
        DataGridTest.fx(() -> {
            String markup = """
                    <?import de.neitzel.fx.control.ToolBarPane?>
                    <?import javafx.scene.control.Label?>
                    <ToolBarPane xmlns:fx="http://javafx.com/fxml/1" dismissible="true" dismissText="Schließen">
                        <Label text="Suche"/>
                    </ToolBarPane>""";
            try {
                de.neitzel.fx.control.ToolBarPane toolbar = new FXMLLoader().load(
                        new ByteArrayInputStream(markup.getBytes(StandardCharsets.UTF_8)));
                var item = toolbar.getItems().getFirst();
                var dismiss = (javafx.scene.control.Button) toolbar.getLeft();
                assertEquals("Schließen", dismiss.getAccessibleText());
                var dismissed = new AtomicBoolean();
                toolbar.setOnDismiss(event -> dismissed.set(true));
                dismiss.fire();
                assertTrue(dismissed.get());
                assertFalse(toolbar.isVisible());
                toolbar.setVisible(true);
                assertSame(item, toolbar.getItems().getFirst());
                toolbar.setDismissible(false);
                assertNull(toolbar.getLeft());
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static DataGrid<Long, Item> load(String fxml, Map<String, Object> namespace)
            throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.getNamespace().putAll(namespace);
        return loader.load(new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void ordinaryFxmlShowsColumnsWithoutStartingQueries() throws Exception {
        DataGridTest.fx(() -> {
            try {
                var grid = load(GRID, Map.of());
                assertNull(grid.stateProperty().get());
                assertTrue(
                        grid.lookupAll(".column-header").stream()
                                .map(Label.class::cast)
                                .anyMatch(l -> l.getText().equals("Text")));
                grid.close();
                assertThrows(IllegalStateException.class, grid::initialize);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    void localItemsRefreshReplaceAndDisposeWithTypedQueriesAndSavedDefaults() throws Exception {
        var ref = new AtomicReference<DataGrid<Long, Item>>();
        var list = FXCollections.observableArrayList(new Item(1, "b", 20), new Item(2, "a", 3));
        var future = new AtomicReference<CompletionStage<Void>>();
        DataGridTest.fx(() -> {
            try {
                ref.set(
                        load(
                                GRID.replace("keyAttribute=", "items=\"$rows\" keyAttribute="),
                                Map.of("rows", list)));
                future.set(ref.get().loadAsync());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        future.get().toCompletableFuture().get(10, TimeUnit.SECONDS);
        DataGridTest.fx(() -> {
            var grid = ref.get();
            assertSame(list, grid.getItems());
            assertEquals(1, grid.stateProperty().get().result().rows().size());
            assertEquals(1, ((PageRequest.Paged) grid.snapshotSetup().page()).pageSize());
            assertEquals(List.of("text"), grid.groupingColumns());
            grid.setSort(new SortKey("count", SortKey.Direction.ASCENDING));
        });
        await(
                ref.get(),
                () ->
                        ref.get().stateProperty().get().result().rows().getFirst().model().count()
                                == 3);
        DataGridTest.fx(() -> list.setAll(new Item(7, "new", 8)));
        await(
                ref.get(),
                () -> ref.get().stateProperty().get().result().rows().getFirst().key() == 7L);
        DataGridTest.fx(() ->
                ref.get()
                        .setItems(
                                FXCollections.observableArrayList(new Item(9, "replacement", 1))));
        await(
                ref.get(),
                () -> ref.get().stateProperty().get().result().rows().getFirst().key() == 9L);
        DataGridTest.fx(() -> {
            long generation = ref.get().stateProperty().get().generation();
            list.add(new Item(10, "detached", 2));
            assertEquals(generation, ref.get().stateProperty().get().generation());
            ref.get().close();
            ref.get().getItems().clear();
        });
        DataGridTest.drain();
        DataGridTest.fx(() ->
                assertEquals(
                        GridCoordinator.Phase.CLOSED, ref.get().stateProperty().get().phase()));
    }

    @Test
    void bindsModelCollectionThroughViewLoader() throws Exception {
        Path document = directory.resolve("bound.fxml");
        Files.writeString(
                document,
                """
                <?import de.neitzel.fx.view.*?>
                <?import javafx.scene.layout.VBox?>
                <?import de.neitzel.fx.control.NonNegativeIntegerSpinner?>
                <VBox xmlns:fx="http://javafx.com/fxml/1">
                    <fx:define><BindingList>
                        <DirectBinding control="grid" property="items" path="rows" direction="view"/>
                        <DirectBinding control="quantity" property="number" path="quantity" direction="both"/>
                    </BindingList></fx:define>
                    <NonNegativeIntegerSpinner fx:id="quantity" minimum="0" maximum="100" initialValue="0"/>
                    <DataGrid fx:id="grid" keyAttribute="id">
                        <columns><DataGridColumn id="text" header="Text"/></columns>
                    </DataGrid>
                </VBox>
                """);
        Model model = new Model();
        model.getRows().add(new Item(1, "bound", 1));
        var ref = new AtomicReference<DataGrid<Long, Item>>();
        var future = new AtomicReference<CompletionStage<Void>>();
        DataGridTest.fx(() -> {
            try {
                var view =
                        ViewLoader.load(
                                document.toUri().toURL(),
                                model,
                                new ApplicationContext("de.neitzel.fx.view.fixture"));
                @SuppressWarnings("unchecked")
                var grid = (DataGrid<Long, Item>) view.getRoot().lookup("#grid");
                ref.set(grid);
                assertSame(model.getRows(), grid.getItems());
                var spinner = (NonNegativeIntegerSpinner) view.getRoot().lookup("#quantity");
                assertEquals(10, spinner.getNumber());
                spinner.getEditor().setText("11");
                assertEquals(11, model.getQuantity());
                @SuppressWarnings("unchecked")
                var quantity = (javafx.beans.property.Property<Number>) view.property("quantity");
                quantity.setValue(17);
                assertEquals(17, spinner.getNumber());
                future.set(grid.loadAsync());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        future.get().toCompletableFuture().get(10, TimeUnit.SECONDS);
        DataGridTest.fx(() -> model.getRows().add(new Item(2, "added", 2)));
        await(ref.get(), () -> ref.get().stateProperty().get().result().rows().size() == 2);
        DataGridTest.fx(() -> ref.get().close());
    }

    @Test
    void externalSourceIsAssignedWithoutLoadingAndUsesTheSameFxmlColumns() throws Exception {
        var calls = new AtomicInteger();
        var source =
                new MemoryGridSource<Long, Item>(
                        () -> {
                            calls.incrementAndGet();
                            return List.of(new Item(5, "external", 10));
                        },
                        Item::id,
                        Long::compareTo,
                        List.of(
                                new MemoryColumn<Item>(
                                        "text",
                                        MemoryColumn.Kind.TEXT,
                                        i -> new FilterValue.Text(i.text())),
                                new MemoryColumn<Item>(
                                        "count",
                                        MemoryColumn.Kind.INTEGER,
                                        i -> new FilterValue.IntegerValue(i.count()))),
                        Runnable::run,
                        250);
        var ref = new AtomicReference<DataGrid<Long, Item>>();
        var future = new AtomicReference<CompletionStage<Void>>();
        DataGridTest.fx(() -> {
            try {
                ref.set(
                        load(
                                GRID.replace("keyAttribute=\"id\"", "source=\"$source\"")
                                        .replace("checklist=\"SOURCE_WIDE\"", ""),
                                Map.of("source", source)));
                assertSame(source, ref.get().getSource());
                assertEquals(0, calls.get());
                future.set(ref.get().loadAsync());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        future.get().toCompletableFuture().get(10, TimeUnit.SECONDS);
        DataGridTest.fx(() -> {
            assertEquals(5L, ref.get().stateProperty().get().result().rows().getFirst().key());
            ref.get().close();
        });
    }

    @Test
    void allPublicControlsCanBeConstructedByOrdinaryFxml() throws Exception {
        DataGridTest.fx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader();
                Parent root =
                        loader.load(
                                new ByteArrayInputStream(
                                        """
                                        <?import de.neitzel.fx.control.*?>
                                        <?import de.neitzel.fx.view.*?>
                                        <?import de.neitzel.fx.docking.*?>
                                        <?import javafx.scene.control.*?>
                                        <?import javafx.scene.layout.VBox?>
                                        <VBox xmlns:fx="http://javafx.com/fxml/1">
                                            <StatusBar fx:id="status"><leftItems><Label text="Ready"/></leftItems></StatusBar>
                                            <ToolBar><items><Button text="Save"/></items></ToolBar>
                                            <NonNegativeIntegerSpinner fx:id="spinner" minimum="2" maximum="20" initialValue="7"/>
                                            <DockPane fx:id="dock"><initialLayout><DockLayout><center>
                                                <DockGroupDefinition destinationId="documents"/>
                                            </center></DockLayout></initialLayout></DockPane>
                                            <Table fx:id="table" collection="rows" row="example.Row">
                                                <columns><Column header="Text" attribute="text"/></columns>
                                            </Table>
                                            <DataGrid fx:id="grid"><columns><DataGridColumn id="text" header="Text"/></columns></DataGrid>
                                        </VBox>
                                        """
                                                .getBytes(StandardCharsets.UTF_8)));
                assertNotNull(root);
                assertEquals(
                        1, ((StatusBar) loader.getNamespace().get("status")).getLeftItems().size());
                var spinner = (NonNegativeIntegerSpinner) loader.getNamespace().get("spinner");
                assertEquals(7, spinner.getValue());
                spinner.getEditor().setText("11");
                assertEquals(11, spinner.getValue());
                spinner.getEditor().setText("21");
                assertEquals("11", spinner.getEditor().getText());
                assertEquals(
                        "documents",
                        ((de.neitzel.fx.docking.DockGroupDefinition)
                                        ((DockPane) loader.getNamespace().get("dock"))
                                                .getInitialLayout()
                                                .getCenter())
                                .getDestinationId());
                ((DataGrid<?, ?>) loader.getNamespace().get("grid")).close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static void await(DataGrid<?, ?> grid, java.util.function.BooleanSupplier condition)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        var done = new AtomicBoolean();
        while (!done.get() && System.nanoTime() < deadline) {
            DataGridTest.fx(() ->
                    done.set(
                            grid.stateProperty().get().phase() == GridCoordinator.Phase.READY
                                    && condition.getAsBoolean()));
            if (!done.get()) Thread.sleep(10);
        }
        assertTrue(done.get(), "Grid did not publish expected rows");
    }
}
