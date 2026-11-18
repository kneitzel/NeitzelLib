package de.neitzel.fx.view;

import de.neitzel.injection.ApplicationContext;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javafx.beans.DefaultProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import lombok.Getter;
import lombok.Setter;

/**
 * A collection shown with one Component for each element.
 * <p>
 * The developer places the Table in the View and names the collection Attribute, the Row
 * class, and each Column. A Column may name a starting width; otherwise the Columns open at
 * equal widths. Filter and Sort change which Rows are showing and in what order.
 * The collection keeps its own order. The body scrolls vertically and the header stays put.
 * Controls after the Column fields stay at their own width.
 */
@DefaultProperty("columns")
public class Table extends VBox {

    /**
     * Style class of the header row.
     */
    static final String HEADER_STYLE = "column-header-row";

    /**
     * Style class of the vertical scroll.
     */
    static final String SCROLL_STYLE = "table-scroll";

    /**
     * Style class of the header text.
     */
    static final String HEADER_TEXT_STYLE = "column-header";

    /**
     * Style class of the drag boundary.
     */
    static final String RESIZE_STYLE = "column-resize";

    /**
     * Style class of the popup opener.
     */
    static final String OPENER_STYLE = "column-opener";

    /**
     * Style class of the sort mark.
     */
    static final String SORT_MARK_STYLE = "sort-mark";

    /**
     * Style class of a selected Row.
     */
    static final String SELECTED_STYLE = "selected";

    /**
     * Style class of the column popup.
     */
    static final String POPUP_STYLE = "column-popup";

    /**
     * The collection Attribute.
     */
    @Getter
    @Setter
    private String collection;

    /**
     * The Row class name.
     */
    private String rowClass;

    /**
     * The Columns, in declared order.
     */
    @Getter
    private final ObservableList<Column> columns = FXCollections.observableArrayList();

    /**
     * The element the user selected, or {@code null}.
     */
    private final ObjectProperty<Object> selection = new SimpleObjectProperty<>(this, "selection");

    /**
     * The header cells, one per Column.
     */
    private final HBox header = new HBox();

    /**
     * Scrolls the header horizontally with the body. It does not scroll vertically.
     */
    private final ScrollPane headerScroll = new ScrollPane();

    /**
     * The vertical scroll. The header is not inside it.
     */
    private final ScrollPane scroll = new ScrollPane();

    /**
     * The Rows currently showing.
     */
    private final VBox body = new VBox();

    /**
     * The column popup.
     */
    private final VBox popup = new VBox();

    /**
     * One Row per element that has been shown. A hidden element keeps its Row.
     */
    private final Map<Object, Node> rows = new IdentityHashMap<>();

    /**
     * The collection this Table shows.
     */
    private ObservableList<Object> elements = FXCollections.observableArrayList();

    /**
     * The width of each Column after the Table has opened. A drag changes one entry.
     */
    private double[] columnWidths;

    /**
     * The Column being sorted, or {@code -1} when the Table shows collection order.
     */
    private int sortIndex = -1;

    /**
     * Whether the Sort is ascending.
     */
    private boolean sortAscending = true;

    /**
     * The checklist in the popup, rebuilt each time the popup opens.
     */
    private final VBox checklist = new VBox();

    /**
     * Narrows which checklist entries are visible. It does not change their checks.
     */
    private final TextField search = new TextField();

    /**
     * Checks or clears every checklist value, including values the search box is hiding.
     */
    private final CheckBox selectAll = new CheckBox("Select all");

    /**
     * The comparison operator for the open Column.
     */
    private final ComboBox<String> comparisonOperator = new ComboBox<>();

    /**
     * The comparison value, written in the checklist's text.
     */
    private final TextField comparisonValue = new TextField();

    /**
     * Stops Select All and the value boxes from updating each other in a loop.
     */
    private boolean adjustingSelectAll;

    /**
     * The element class of the collection.
     */
    private Class<?> elementType;

    /**
     * One Filter per Column. A null entry means that Column has no Filter yet.
     */
    private final List<ValueFilter> valueFilters = new ArrayList<>();
    /**
     * Index of the Column whose popup is open, or {@code -1} when no popup is open.
     */
    private int popupIndex = -1;

    /**
     * Whether {@link #open} has already run.
     */
    private boolean opened;

    /**
     * The container that creates Rows.
     */
    private ApplicationContext context;

    /**
     * The Row class.
     */
    private Class<?> rowType;

    /** True once the explicit queried-result rendering path has been selected. */
    private boolean resultMode;

    /**
     * Opens the opt-in queried-result renderer on a fresh Table. Existing FXML/collection behavior
     * is unchanged. This must be called on the FX thread before legacy ViewLoader initialization.
     * The returned renderer owns rows until closed; its factory supplies mapped, already-bound nodes.
     */
    public <K, T> TableResults<K, T> openResults(List<ResultColumn> definitions,
            java.util.function.Function<T, ResultRow> factory) {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Use the JavaFX application thread");
        }
        if (opened || resultMode) {
            throw new IllegalStateException("Table is already open");
        }
        TableResults<K, T> renderer = new TableResults<>(header, body, List.copyOf(definitions), factory, headerScroll, scroll);
        // DataGrid grows this Table; let its viewport consume that height as well.
        VBox.setVgrow(scroll, Priority.ALWAYS);
        resultMode = true;
        return renderer;
    }

    /**
     * Creates a Table and prepares its header, scrolling body, and popup.
     */
    public Table() {
        getStyleClass().add("table");
        header.getStyleClass().add(HEADER_STYLE);
        headerScroll.setContent(header);
        headerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        headerScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        headerScroll.setFitToHeight(true);
        headerScroll.getStyleClass().add("table-header-scroll");
        scroll.getStyleClass().add(SCROLL_STYLE);
        scroll.setContent(body);
        scroll.setFitToWidth(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.hvalueProperty().bindBidirectional(headerScroll.hvalueProperty());
        popup.getStyleClass().add(POPUP_STYLE);
        popup.setVisible(false);
        popup.setManaged(false);
        getChildren().addAll(headerScroll, scroll, popup);
        body.setOnMouseClicked(this::clearSelectionFromBlank);
        selection.addListener((observable, previous, current) -> markSelection());
        addEventFilter(MouseEvent.MOUSE_CLICKED, this::dismissPopup);
        search.getStyleClass().add("column-search");
        search.textProperty().addListener((observable, previous, current) -> applySearch());
        selectAll.getStyleClass().add("column-select-all");
        selectAll.setAllowIndeterminate(true);
        selectAll.setOnMouseClicked(event -> applySelectAll());
        comparisonOperator.getStyleClass().add("column-comparison-operator");
        comparisonValue.getStyleClass().add("column-comparison-value");
        buildSortCommands();
    }

    /**
     * Sets the Row class name.
     *
     * @param row the Row class name
     */
    public void setRow(String row) {
        this.rowClass = row;
    }

    /**
     * Returns the Row class name.
     *
     * @return the Row class name
     */
    public String getRow() {
        return rowClass;
    }

    /**
     * Returns the selection property.
     * <p>
     * A Direct binding can tie this property to the parent Model's Selection.
     *
     * @return the selection property
     */
    public ObjectProperty<Object> selectionProperty() {
        return selection;
    }

    /**
     * Returns the selected element.
     *
     * @return the selected element, or {@code null}
     */
    public Object getSelection() {
        return selection.get();
    }

    /**
     * Sets the selected element.
     *
     * @param selection the selected element, or {@code null}
     */
    public void setSelection(Object selection) {
        this.selection.set(selection);
    }

    /**
     * Shows every Table under {@code root}.
     *
     * @param root      the View root
     * @param viewModel the ViewModel for this View
     * @param context   the container that creates each Row
     */
    static void openAll(Parent root, Object viewModel, ApplicationContext context) {
        walk(root, viewModel, context);
    }

    /**
     * Opens this Table for the ViewModel.
     *
     * @param viewModel the ViewModel for this View
     * @param context   the container that creates each Row
     */
    void open(Object viewModel, ApplicationContext context) {
        if (resultMode) {
            return;
        }
        if (opened) {
            return;
        }
        opened = true;
        this.context = context;
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("A table needs a column");
        }
        requireDistinctAttributes();
        elements = elements(viewModel);
        elementType = Attributes.elementType(modelOf(viewModel), collection);
        requireSimpleColumns(elementType);
        rowType = rowType();
        valueFilters.clear();
        for (int index = 0; index < columns.size(); index++) {
            valueFilters.add(new ValueFilter());
        }
        buildHeader();
        ensureWidths();
        elements.addListener((ListChangeListener<Object>) this::onCollectionChange);
        refresh();
    }

    /**
     * Builds the sort commands at the top of the popup.
     */
    private void buildSortCommands() {
        popup.getChildren().add(command("Ascending", () -> chooseSort(true)));
        popup.getChildren().add(command("Descending", () -> chooseSort(false)));
        popup.getChildren().add(command("Clear sort", this::clearSort));
        popup.getChildren().add(command("Clear filter", this::clearFilter));
        popup.getChildren().add(search);
        popup.getChildren().add(selectAll);
        popup.getChildren().add(checklist);
        popup.getChildren().add(comparisonOperator);
        popup.getChildren().add(comparisonValue);
        popup.getChildren().add(command("Confirm", this::confirmChecklist));
    }

    /**
     * Creates a popup command.
     *
     * @param text   the label
     * @param action the action
     * @return the button
     */
    private static Button command(String text, Runnable action) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> action.run());
        return button;
    }

    /**
     * Rejects a Table that names one Attribute twice.
     */
    private void requireDistinctAttributes() {
        Set<String> seen = new HashSet<>();
        for (Column column : columns) {
            if (column.getAttribute() == null || column.getHeader() == null || column.getHeader().isBlank()) {
                throw new IllegalArgumentException("A column needs a header and an attribute");
            }
            if (!seen.add(column.getAttribute())) {
                throw new IllegalArgumentException("Attribute \"" + column.getAttribute() + "\" is used twice");
            }
            if (column.hasWidth() && !(column.getWidth() > 0 && column.getWidth() < Double.POSITIVE_INFINITY)) {
                throw new IllegalArgumentException("Column \"" + column.getHeader() + "\" needs a positive width");
            }
        }
    }

    /**
     * Rejects a Column whose Attribute is not a simple value.
     *
     * @param elementType the element class
     */
    private void requireSimpleColumns(Class<?> elementType) {
        for (Column column : columns) {
            Class<?> type = Attributes.type(elementType, column.getAttribute());
            if (!Attributes.simple(type)) {
                throw new IllegalArgumentException(
                        "Attribute \"" + column.getAttribute() + "\" is not a simple value");
            }
        }
    }

    /**
     * Builds one header cell per Column.
     */
    private void buildHeader() {
        header.getChildren().clear();
        for (int index = 0; index < columns.size(); index++) {
            header.getChildren().add(headerCell(index));
        }
    }

    /**
     * Creates the header cell for one Column.
     *
     * @param index the Column index
     * @return the cell
     */
    private HBox headerCell(int index) {
        Column column = columns.get(index);
        Label title = new Label(column.getHeader());
        title.getStyleClass().add(HEADER_TEXT_STYLE);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);
        Label mark = new Label();
        mark.getStyleClass().add(SORT_MARK_STYLE);
        mark.setVisible(false);
        mark.setManaged(false);
        Button opener = new Button();
        opener.getStyleClass().add(OPENER_STYLE);
        opener.setOnAction(event -> showPopup(index));
        Region handle = new Region();
        handle.getStyleClass().add(RESIZE_STYLE);
        handle.setMinWidth(6);
        handle.setPrefWidth(6);
        handle.setOnMousePressed(event -> beginResize(index, event));
        handle.setOnMouseDragged(event -> dragResize(index, event));
        HBox cell = new HBox(title, mark, opener, handle);
        cell.getStyleClass().add("column-cell");
        cell.setOnMouseClicked(event -> {
            if (isInside(opener, event.getTarget()) || isInside(handle, event.getTarget())) {
                return;
            }
            toggleSort(index);
        });
        cell.setUserData(new double[] {0, 0});
        return cell;
    }

    /**
     * Remembers where a resize drag started.
     *
     * @param index the Column index
     * @param event the press
     */
    private void beginResize(int index, MouseEvent event) {
        HBox cell = (HBox) header.getChildren().get(index);
        cell.setUserData(new double[] {event.getScreenX(), columnWidths[index]});
        event.consume();
    }

    /**
     * Changes one Column's width. The other Columns stay as they are.
     *
     * @param index the Column index
     * @param event the drag
     */
    private void dragResize(int index, MouseEvent event) {
        HBox cell = (HBox) header.getChildren().get(index);
        double[] start = (double[]) cell.getUserData();
        double next = start[1] + (event.getScreenX() - start[0]);
        columnWidths[index] = Math.max(minimumWidth(index), next);
        applyWidths();
        event.consume();
    }

    /**
     * Returns the narrowest width that still shows the header text.
     *
     * @param index the Column index
     * @return the minimum width
     */
    private double minimumWidth(int index) {
        Text text = new Text(columns.get(index).getHeader());
        return Math.ceil(text.getLayoutBounds().getWidth()) + 8;
    }

    /**
     * Shows the popup for one Column.
     *
     * @param index the Column index
     */
    private void showPopup(int index) {
        popupIndex = index;
        search.setText("");
        fillChecklist(index);
        applySearch();
        syncSelectAll();
        showComparison(index);
        popup.setVisible(true);
        popup.setManaged(true);
    }

    /**
     * Hides checklist entries whose text does not contain the search, ignoring case.
     * <p>
     * A {@code *} is an ordinary character. Checks are left as they are.
     */
    private void applySearch() {
        String query = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
        for (Node node : checklist.getChildren()) {
            if (!(node instanceof CheckBox box)) {
                continue;
            }
            String text = box.getText() == null ? "" : box.getText().toLowerCase(Locale.ROOT);
            boolean shown = query.isEmpty() || text.contains(query);
            box.setVisible(shown);
            box.setManaged(shown);
        }
    }

    /**
     * Checks or clears every value, including values the search box is hiding.
     */
    private void applySelectAll() {
        if (adjustingSelectAll) {
            return;
        }
        adjustingSelectAll = true;
        boolean selected = !everyBoxSelected();
        selectAll.setIndeterminate(false);
        for (Node node : checklist.getChildren()) {
            if (node instanceof CheckBox box) {
                box.setSelected(selected);
            }
        }
        adjustingSelectAll = false;
        syncSelectAll();
    }

    /**
     * Reports whether every checklist value is checked.
     *
     * @return {@code true} when every value is checked
     */
    private boolean everyBoxSelected() {
        boolean any = false;
        for (Node node : checklist.getChildren()) {
            if (!(node instanceof CheckBox box)) {
                continue;
            }
            any = true;
            if (!box.isSelected()) {
                return false;
            }
        }
        return any;
    }

    /**
     * Shows Select All as checked, unchecked, or mixed from every value.
     */
    private void syncSelectAll() {
        if (adjustingSelectAll) {
            return;
        }
        int total = 0;
        int selected = 0;
        for (Node node : checklist.getChildren()) {
            if (!(node instanceof CheckBox box)) {
                continue;
            }
            total++;
            if (box.isSelected()) {
                selected++;
            }
        }
        adjustingSelectAll = true;
        if (total == 0 || selected == total) {
            selectAll.setIndeterminate(false);
            selectAll.setSelected(true);
        } else if (selected == 0) {
            selectAll.setIndeterminate(false);
            selectAll.setSelected(false);
        } else {
            selectAll.setSelected(false);
            selectAll.setIndeterminate(true);
        }
        adjustingSelectAll = false;
    }

    /**
     * Fills the checklist with the values this Column can filter, blank first.
     *
     * @param index the Column index
     */
    private void fillChecklist(int index) {
        checklist.getChildren().clear();
        ValueFilter filter = valueFilters.get(index);
        List<Object> values = checklistValues(index);
        for (Object value : values) {
            CheckBox box = new CheckBox(checklistText(value));
            box.setUserData(value);
            box.setSelected(!filter.active || filter.comparison != Comparison.NONE || filter.matches(value));
            box.selectedProperty().addListener((observable, previous, current) -> syncSelectAll());
            checklist.getChildren().add(box);
        }
    }

    /**
     * Returns the distinct values that pass every other Column's Filter.
     *
     * @param index the Column whose own Filter is ignored
     * @return the values, blank first, then ascending
     */
    private List<Object> checklistValues(int index) {
        List<Object> values = new ArrayList<>();
        for (Object element : elements) {
            if (!passes(element, index)) {
                continue;
            }
            Object value = read(reader(element.getClass(), columns.get(index).getAttribute()), element);
            if (!values.contains(value)) {
                values.add(value);
            }
        }
        values.sort(this::checklistOrder);
        return values;
    }

    /**
     * Orders checklist values with a missing value first.
     *
     * @param left  one value
     * @param right the other value
     * @return the order
     */
    private int checklistOrder(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return compareValues(left, right);
    }

    /**
     * Returns the text shown for a checklist value.
     *
     * @param value the Attribute value, or {@code null}
     * @return the text
     */
    private static String checklistText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    /**
     * Shows the comparison for a string, number, or date Column, and hides it otherwise.
     *
     * @param index the Column index
     */
    private void showComparison(int index) {
        Class<?> type = Attributes.type(elementType, columns.get(index).getAttribute());
        boolean offered = comparisonOffered(index);
        comparisonOperator.setVisible(offered);
        comparisonOperator.setManaged(offered);
        comparisonValue.setVisible(offered);
        comparisonValue.setManaged(offered);
        if (!offered) {
            comparisonOperator.setValue(null);
            comparisonValue.setText("");
            return;
        }
        comparisonOperator.getItems().setAll(operatorNames(type));
        ValueFilter filter = valueFilters.get(index);
        if (filter.comparison == Comparison.NONE) {
            comparisonOperator.setValue(null);
            comparisonValue.setText("");
        } else {
            comparisonOperator.setValue(comparisonName(filter.comparison));
            comparisonValue.setText(filter.comparisonText);
        }
    }

    /**
     * Reports whether this Column offers a comparison.
     *
     * @param index the Column index
     * @return {@code true} for a string, number, or date or time
     */
    private boolean comparisonOffered(int index) {
        Class<?> type = Attributes.type(elementType, columns.get(index).getAttribute());
        if (type == Boolean.class || type == boolean.class || type.isEnum()) {
            return false;
        }
        return type == String.class || number(type) || time(type);
    }

    /**
     * Returns the operator names for a Column type.
     *
     * @param type the Attribute type
     * @return the operator names
     */
    private static List<String> operatorNames(Class<?> type) {
        if (type == String.class) {
            return List.of("Contains", "Starts with", "Ends with");
        }
        if (number(type)) {
            return List.of("Greater than", "Less than");
        }
        return List.of("Before", "After");
    }

    /**
     * Reports whether the user has started a comparison.
     *
     * @return {@code true} when an operator or a value is present
     */
    private boolean comparisonAttempted() {
        boolean operatorChosen = comparisonOperator.getValue() != null && !comparisonOperator.getValue().isBlank();
        boolean textEntered = comparisonValue.getText() != null && !comparisonValue.getText().isBlank();
        return operatorChosen || textEntered;
    }

    /**
     * Reports whether the comparison can be applied.
     *
     * @param index the Column index
     * @return {@code true} when the operator and the value both fit the Attribute
     */
    private boolean comparisonValid(int index) {
        String operator = comparisonOperator.getValue();
        String text = comparisonValue.getText() == null ? "" : comparisonValue.getText().trim();
        if (operator == null || operator.isBlank() || text.isEmpty()) {
            return false;
        }
        if (comparisonKind(operator) == Comparison.NONE) {
            return false;
        }
        return parses(Attributes.type(elementType, columns.get(index).getAttribute()), text);
    }

    /**
     * Returns the comparison named by the popup.
     *
     * @param name the operator name
     * @return the comparison, or {@link Comparison#NONE}
     */
    private static Comparison comparisonKind(String name) {
        if (name == null) {
            return Comparison.NONE;
        }
        return switch (name) {
            case "Contains" -> Comparison.CONTAINS;
            case "Starts with" -> Comparison.STARTS;
            case "Ends with" -> Comparison.ENDS;
            case "Greater than" -> Comparison.GREATER;
            case "Less than" -> Comparison.LESS;
            case "Before" -> Comparison.BEFORE;
            case "After" -> Comparison.AFTER;
            default -> Comparison.NONE;
        };
    }

    /**
     * Returns the popup name of a comparison.
     *
     * @param comparison the comparison
     * @return the operator name
     */
    private static String comparisonName(Comparison comparison) {
        return switch (comparison) {
            case CONTAINS -> "Contains";
            case STARTS -> "Starts with";
            case ENDS -> "Ends with";
            case GREATER -> "Greater than";
            case LESS -> "Less than";
            case BEFORE -> "Before";
            case AFTER -> "After";
            case NONE -> "";
        };
    }

    /**
     * Reports whether {@code text} is the checklist form of {@code type}.
     *
     * @param type the Attribute type
     * @param text the comparison value
     * @return {@code true} when the text parses
     */
    private static boolean parses(Class<?> type, String text) {
        try {
            if (type == String.class) {
                return true;
            }
            if (integral(type)) {
                Long.parseLong(text);
                return true;
            }
            if (number(type)) {
                Double.valueOf(text);
                return true;
            }
            return parsedTime(type, text) != null;
        } catch (NumberFormatException failure) {
            return false;
        }
    }

    /**
     * Reports whether the type is a whole number.
     *
     * @param type the Attribute type
     * @return {@code true} for an integer type
     */
    private static boolean integral(Class<?> type) {
        return type == Integer.class || type == int.class
                || type == Long.class || type == long.class
                || type == Short.class || type == short.class
                || type == Byte.class || type == byte.class;
    }

    /**
     * Reports whether the type is a number.
     *
     * @param type the Attribute type
     * @return {@code true} for a number
     */
    private static boolean number(Class<?> type) {
        return integral(type) || type == Double.class || type == double.class
                || type == Float.class || type == float.class || Number.class.isAssignableFrom(type);
    }

    /**
     * Reports whether the type is a date or time.
     *
     * @param type the Attribute type
     * @return {@code true} for a date or time
     */
    private static boolean time(Class<?> type) {
        return TemporalAccessor.class.isAssignableFrom(type)
                || Date.class.isAssignableFrom(type)
                || Calendar.class.isAssignableFrom(type);
    }

    /**
     * Parses a date or time in the checklist's text.
     *
     * @param type the Attribute type
     * @param text the comparison value
     * @return the parsed value, or {@code null}
     */
    private static Object parsedTime(Class<?> type, String text) {
        try {
            if (type == LocalDate.class) {
                return LocalDate.parse(text);
            }
            if (type == LocalDateTime.class) {
                return LocalDateTime.parse(text);
            }
            if (type == Instant.class) {
                return Instant.parse(text);
            }
            if (Date.class.isAssignableFrom(type)) {
                return Date.from(Instant.parse(text));
            }
            if (Calendar.class.isAssignableFrom(type)) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(Date.from(Instant.parse(text)));
                return calendar;
            }
            return null;
        } catch (DateTimeParseException failure) {
            return null;
        }
    }

    /**
     * Applies the checklist and closes the popup.
     */
    private void confirmChecklist() {
        ValueFilter filter = valueFilters.get(popupIndex);
        if (comparisonOffered(popupIndex) && comparisonAttempted()) {
            if (!comparisonValid(popupIndex)) {
                return;
            }
            filter.comparison = comparisonKind(comparisonOperator.getValue());
            filter.comparisonText = comparisonValue.getText().trim();
            filter.active = true;
            filter.values.clear();
            filter.includeMissing = false;
            hidePopup();
            refresh();
            return;
        }
        filter.comparison = Comparison.NONE;
        filter.comparisonText = "";
        int boxes = checklist.getChildren().size();
        int checked = 0;
        filter.values.clear();
        filter.includeMissing = false;
        for (Node node : checklist.getChildren()) {
            CheckBox box = (CheckBox) node;
            if (!box.isSelected()) {
                continue;
            }
            checked++;
            if (box.getUserData() == null) {
                filter.includeMissing = true;
            } else {
                filter.values.add(box.getUserData());
            }
        }
        filter.active = checked != boxes;
        hidePopup();
        refresh();
    }

    /**
     * Removes the open Column's Filter and closes the popup.
     */
    private void clearFilter() {
        if (popupIndex >= 0 && popupIndex < valueFilters.size()) {
            ValueFilter filter = valueFilters.get(popupIndex);
            filter.active = false;
            filter.comparison = Comparison.NONE;
            filter.comparisonText = "";
        }
        hidePopup();
        refresh();
    }

    /**
     * Reports whether an element passes the Filters, optionally ignoring one Column.
     *
     * @param element the element
     * @param except  the Column index to ignore, or {@code -1}
     * @return {@code true} when the element stays visible
     */
    private boolean passes(Object element, int except) {
        for (int index = 0; index < valueFilters.size(); index++) {
            if (index == except || !valueFilters.get(index).active) {
                continue;
            }
            Object value = read(reader(element.getClass(), columns.get(index).getAttribute()), element);
            if (!valueFilters.get(index).matches(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Hides the popup when the press is outside it.
     *
     * @param event the press
     */
    private void dismissPopup(MouseEvent event) {
        if (!popup.isVisible() || isInside(popup, event.getTarget())) {
            return;
        }
        if (isInside(opener(popupIndex), event.getTarget())) {
            return;
        }
        hidePopup();
    }

    /**
     * Hides the popup.
     */
    private void hidePopup() {
        popup.setVisible(false);
        popup.setManaged(false);
    }

    /**
     * Returns the opener of a Column, or {@code null}.
     *
     * @param index the Column index
     * @return the opener
     */
    private Node opener(int index) {
        if (index < 0 || index >= header.getChildren().size()) {
            return null;
        }
        return header.getChildren().get(index).lookup("." + OPENER_STYLE);
    }

    /**
     * Reports whether {@code target} is {@code node} or inside it.
     *
     * @param node   the ancestor
     * @param target the event target
     * @return {@code true} when the target is inside the node
     */
    private static boolean isInside(Node node, Object target) {
        if (!(target instanceof Node current) || node == null) {
            return false;
        }
        while (current != null) {
            if (current == node) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Switches the Sort between ascending and descending, or starts at ascending.
     *
     * @param index the Column index
     */
    private void toggleSort(int index) {
        if (sortIndex == index) {
            sortAscending = !sortAscending;
        } else {
            sortIndex = index;
            sortAscending = true;
        }
        refresh();
    }

    /**
     * Sorts by the popup's Column and closes the popup.
     *
     * @param ascending whether the Sort is ascending
     */
    private void chooseSort(boolean ascending) {
        sortIndex = popupIndex;
        sortAscending = ascending;
        hidePopup();
        refresh();
    }

    /**
     * Clears the Sort and closes the popup.
     */
    private void clearSort() {
        sortIndex = -1;
        hidePopup();
        refresh();
    }

    /**
     * Clears Selection when the click lands on the blank body.
     *
     * @param event the click
     */
    private void clearSelectionFromBlank(MouseEvent event) {
        if (event.getTarget() == body && event.getButton() == MouseButton.PRIMARY) {
            selection.set(null);
        }
    }

    /**
     * Returns the collection the Table shows.
     *
     * @param viewModel the ViewModel for this View
     * @return the collection
     */
    @SuppressWarnings("unchecked")
    private ObservableList<Object> elements(Object viewModel) {
        Property<?> property = viewModel instanceof ViewModel model
                ? model.property(collection)
                : NamedProperties.get(viewModel, collection);
        if (property == null || !(property.getValue() instanceof ObservableList<?> values)) {
            throw new IllegalArgumentException("No collection \"" + collection + "\"");
        }
        return (ObservableList<Object>) values;
    }

    /**
     * Returns the Model behind the ViewModel.
     *
     * @param viewModel the ViewModel for this View
     * @return the Model
     */
    private static Object modelOf(Object viewModel) {
        if (viewModel instanceof ViewModel model) {
            return model.model();
        }
        return viewModel;
    }

    /**
     * Returns the Row class named on this Table.
     *
     * @return the Row class
     */
    private Class<?> rowType() {
        try {
            return Class.forName(rowClass);
        } catch (ClassNotFoundException failure) {
            throw new IllegalArgumentException("No row " + rowClass, failure);
        }
    }

    /**
     * Follows adds and removes. A permutation of the collection is left as it is.
     *
     * @param change the collection change
     */
    private void onCollectionChange(ListChangeListener.Change<?> change) {
        while (change.next()) {
            if (change.wasPermutated() || change.wasUpdated()) {
                continue;
            }
            for (Object removed : change.getRemoved()) {
                rows.remove(removed);
                if (selection.get() == removed) {
                    selection.set(null);
                }
            }
        }
        refresh();
    }

    /**
     * Shows the Rows that pass the Filter, in Sort order, reusing each element's Row.
     */
    private void refresh() {
        List<Object> shown = new ArrayList<>();
        for (Object element : elements) {
            if (passes(element, -1)) {
                shown.add(element);
            }
        }
        if (sortIndex >= 0 && !shown.isEmpty()) {
            shown.sort(comparator());
        }
        body.getChildren().clear();
        for (Object element : shown) {
            body.getChildren().add(rows.computeIfAbsent(element, this::createRow));
        }
        applyWidths();
        markSelection();
        updateSortMarks();
    }

    /**
     * Returns a comparator that keeps equal values in collection order and missing values last.
     *
     * @return the comparator
     */
    private Comparator<Object> comparator() {
        Method reader = reader(elements.get(0).getClass(), columns.get(sortIndex).getAttribute());
        return (left, right) -> compare(reader, left, right);
    }

    /**
     * Compares two elements by the sorted Column.
     *
     * @param reader the Attribute getter
     * @param left   one element
     * @param right  the other element
     * @return the comparison, with missing values last
     */
    private int compare(Method reader, Object left, Object right) {
        Object leftValue = read(reader, left);
        Object rightValue = read(reader, right);
        if (leftValue == null && rightValue == null) {
            return 0;
        }
        if (leftValue == null) {
            return 1;
        }
        if (rightValue == null) {
            return -1;
        }
        int compared = compareValues(leftValue, rightValue);
        return sortAscending ? compared : -compared;
    }

    /**
     * Compares two present values.
     *
     * @param left  one value
     * @param right the other value
     * @return the comparison
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object left, Object right) {
        if (left instanceof String leftText && right instanceof String rightText) {
            return leftText.compareToIgnoreCase(rightText);
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
        }
        if (left instanceof Comparable comparable && left.getClass() == right.getClass()) {
            return comparable.compareTo(right);
        }
        return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
    }

    /**
     * Reads an Attribute.
     *
     * @param reader  the getter
     * @param element the element
     * @return the value, or {@code null}
     */
    private static Object read(Method reader, Object element) {
        try {
            return reader.invoke(element);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not read " + reader.getName(), failure);
        }
    }

    /**
     * Returns the getter for an Attribute.
     *
     * @param type the element class
     * @param name the Attribute
     * @return the getter
     */
    private static Method reader(Class<?> type, String name) {
        try {
            java.beans.BeanInfo info = java.beans.Introspector.getBeanInfo(type, Object.class);
            for (PropertyDescriptor descriptor : info.getPropertyDescriptors()) {
                if (descriptor.getName().equals(name) && descriptor.getReadMethod() != null) {
                    return descriptor.getReadMethod();
                }
            }
        } catch (java.beans.IntrospectionException failure) {
            throw new IllegalStateException("Could not introspect " + type.getName(), failure);
        }
        throw new IllegalArgumentException("No attribute \"" + name + "\"");
    }

    /**
     * Creates one Row and sets its model to {@code element}.
     *
     * @param element the element
     * @return the Row
     */
    private Node createRow(Object element) {
        Object component = Components.fresh(rowType, context);
        Components.setModel(component, element);
        if (!(component instanceof Node node)) {
            throw new IllegalArgumentException("Row is not a node: " + rowType.getName());
        }
        node.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                selection.set(element);
            }
        });
        watchEdits(node);
        return node;
    }

    /**
     * Refreshes the Table when a field in the Row is edited.
     *
     * @param node the Row or one of its fields
     */
    private void watchEdits(Node node) {
        if (node instanceof javafx.scene.control.TextInputControl input) {
            input.textProperty().addListener((observable, previous, current) -> refresh());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                watchEdits(child);
            }
        }
    }

    /**
     * Marks the visible Row for the current Selection.
     */
    private void markSelection() {
        for (Node node : rows.values()) {
            node.getStyleClass().remove(SELECTED_STYLE);
        }
        Node selected = rows.get(selection.get());
        if (selected != null && body.getChildren().contains(selected)
                && !selected.getStyleClass().contains(SELECTED_STYLE)) {
            selected.getStyleClass().add(SELECTED_STYLE);
        }
    }

    /**
     * Shows the sort mark only on the sorted Column.
     */
    private void updateSortMarks() {
        for (int index = 0; index < header.getChildren().size(); index++) {
            Label mark = (Label) header.getChildren().get(index).lookup("." + SORT_MARK_STYLE);
            boolean sorting = index == sortIndex;
            mark.setVisible(sorting);
            mark.setManaged(sorting);
            mark.setText(sortAscending ? "ascending" : "descending");
            Node opener = header.getChildren().get(index).lookup("." + OPENER_STYLE);
            boolean filtered = index < valueFilters.size() && valueFilters.get(index).active;
            if (opener != null && filtered && !opener.getStyleClass().contains("filtered")) {
                opener.getStyleClass().add("filtered");
            }
            if (opener != null && !filtered) {
                opener.getStyleClass().remove("filtered");
            }
        }
    }

    /**
     * Gives each Column its width.
     * <p>
     * Columns with no starting width share the Table width equally. A starting width is kept, and
     * a later change of the Table's width does not redistribute the Columns. A starting width
     * narrower than the header text opens at the header text.
     */
    private void ensureWidths() {
        if (columnWidths != null && columnWidths.length == columns.size()) {
            return;
        }
        columnWidths = new double[columns.size()];
        if (!anyStartingWidth()) {
            java.util.Arrays.fill(columnWidths, tableWidth() / columns.size());
            return;
        }
        double specified = 0;
        int open = 0;
        for (int index = 0; index < columns.size(); index++) {
            Column column = columns.get(index);
            if (column.hasWidth()) {
                double chosen = Math.max(column.getWidth(), minimumWidth(index));
                columnWidths[index] = chosen;
                specified += chosen;
            } else {
                open++;
            }
        }
        if (open == 0) {
            return;
        }
        double share = (tableWidth() - specified) / open;
        for (int index = 0; index < columns.size(); index++) {
            if (!columns.get(index).hasWidth()) {
                columnWidths[index] = Math.max(share, minimumWidth(index));
            }
        }
    }

    /**
     * Returns whether any Column names a starting width.
     *
     * @return {@code true} when at least one Column has a starting width
     */
    private boolean anyStartingWidth() {
        for (Column column : columns) {
            if (column.hasWidth()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the width the equal shares are taken from.
     *
     * @return the Table width, or a fallback when the Table has not been laid out
     */
    private double tableWidth() {
        double width = getWidth() > 0 ? getWidth() : getPrefWidth();
        if (width <= 0) {
            return columns.size() * 80.0;
        }
        return width;
    }

    /**
     * Applies the stored Column widths to the header and the fields.
     * <p>
     * The first fields, one per Column, take those widths. Controls after them keep their own
     * size. Spacing on an {@link HBox} row is repeated between the header cells.
     */
    private void applyWidths() {
        if (columns.isEmpty() || columnWidths == null) {
            return;
        }
        double columnSum = 0;
        for (double width : columnWidths) {
            columnSum += width;
        }
        double spacing = rowSpacing();
        header.setSpacing(spacing);
        double headerWidth = columnSum + spacing * Math.max(0, columns.size() - 1);
        header.setPrefWidth(headerWidth);
        header.setMinWidth(headerWidth);
        for (int index = 0; index < header.getChildren().size(); index++) {
            fixWidth((Region) header.getChildren().get(index), columnWidths[index]);
        }
        for (Node rowNode : body.getChildren()) {
            double rowWidth = rowNode instanceof HBox box ? widthOf(box, columnSum) : headerWidth;
            if (rowNode instanceof Region region) {
                region.setPrefWidth(rowWidth);
                region.setMinWidth(rowWidth);
            }
            if (rowNode instanceof Parent parent) {
                int index = 0;
                for (Node field : parent.getChildrenUnmodifiable()) {
                    if (index >= columnWidths.length) {
                        break;
                    }
                    if (field instanceof Region region) {
                        fixWidth(region, columnWidths[index]);
                    }
                    index++;
                }
            }
        }
    }

    /**
     * Returns the spacing of a Row, so the header can use the same gaps.
     *
     * @return the spacing, or {@code 0} when no Row is an {@link HBox}
     */
    private double rowSpacing() {
        for (Node row : body.getChildren()) {
            if (row instanceof HBox box) {
                return box.getSpacing();
            }
        }
        for (Node row : rows.values()) {
            if (row instanceof HBox box) {
                return box.getSpacing();
            }
        }
        return 0;
    }

    /**
     * Returns the width that fits the Columns and any controls after them.
     *
     * @param box       the Row
     * @param columnSum the sum of the Column widths
     * @return the Row width
     */
    private double widthOf(HBox box, double columnSum) {
        double width = columnSum + box.getInsets().getLeft() + box.getInsets().getRight();
        if (box.getChildren().size() > 1) {
            width += box.getSpacing() * (box.getChildren().size() - 1);
        }
        for (int index = columnWidths.length; index < box.getChildren().size(); index++) {
            Node child = box.getChildren().get(index);
            if (child.isManaged() && child instanceof Region region) {
                width += region.prefWidth(-1);
            }
        }
        return width;
    }

    /**
     * Fixes one node's width.
     *
     * @param region the header cell or field
     * @param width  the Column width
     */
    private static void fixWidth(Region region, double width) {
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
    }

    /**
     * Opens every Table in {@code node} and below it.
     *
     * @param node      a node in the View
     * @param viewModel the ViewModel for this View
     * @param context   the container that creates each Row
     */
    private static void walk(Node node, Object viewModel, ApplicationContext context) {
        if (node instanceof Table table) {
            table.open(viewModel, context);
        }
        if (!(node instanceof Parent parent)) {
            return;
        }
        for (Node child : parent.getChildrenUnmodifiable()) {
            walk(child, viewModel, context);
        }
        if (parent instanceof ScrollPane pane && pane.getContent() != null) {
            walk(pane.getContent(), viewModel, context);
        }
    }

    /**
     * The set of Attribute values a Column is showing.
     */
    private static final class ValueFilter {

        /**
         * Whether this Filter is applied. All values checked leaves it inactive.
         */
        private boolean active;

        /**
         * Whether a missing value is included.
         */
        private boolean includeMissing;

        /**
         * The confirmed values, not including a missing value.
         */
        private final Set<Object> values = new HashSet<>();

        /**
         * The comparison that replaced the value set, or {@link Comparison#NONE}.
         */
        private Comparison comparison = Comparison.NONE;

        /**
         * The comparison value in checklist text.
         */
        private String comparisonText = "";

        /**
         * Reports whether a value passes this Filter.
         *
         * @param value the Attribute value, or {@code null}
         * @return {@code true} when the value is included
         */
        private boolean matches(Object value) {
            if (comparison != Comparison.NONE) {
                return matchesComparison(value);
            }
            if (value == null) {
                return includeMissing;
            }
            return values.contains(value);
        }

        /**
         * Reports whether a value passes the comparison. A missing value does not.
         *
         * @param value the Attribute value, or {@code null}
         * @return {@code true} when the comparison matches
         */
        private boolean matchesComparison(Object value) {
            if (value == null) {
                return false;
            }
            String shown = checklistText(value).toLowerCase(Locale.ROOT);
            String query = comparisonText.toLowerCase(Locale.ROOT);
            return switch (comparison) {
                case CONTAINS -> shown.contains(query);
                case STARTS -> shown.startsWith(query);
                case ENDS -> shown.endsWith(query);
                case GREATER -> compareNumber(value, comparisonText) > 0;
                case LESS -> compareNumber(value, comparisonText) < 0;
                case BEFORE -> compareTime(value, comparisonText) < 0;
                case AFTER -> compareTime(value, comparisonText) > 0;
                case NONE -> false;
            };
        }

        /**
         * Compares a number with the comparison text. The boundary is not a match.
         *
         * @param value the Attribute value
         * @param text  the comparison text
         * @return the comparison
         */
        private static int compareNumber(Object value, String text) {
            double left = ((Number) value).doubleValue();
            double right = Double.parseDouble(text);
            return Double.compare(left, right);
        }

        /**
         * Compares a date or time with the comparison text. The boundary is not a match.
         *
         * @param value the Attribute value
         * @param text  the comparison text
         * @return the comparison
         */
        private static int compareTime(Object value, String text) {
            if (value instanceof LocalDate date) {
                return date.compareTo(LocalDate.parse(text));
            }
            if (value instanceof LocalDateTime date) {
                return date.compareTo(LocalDateTime.parse(text));
            }
            if (value instanceof Instant instant) {
                return instant.compareTo(Instant.parse(text));
            }
            if (value instanceof Date date) {
                return date.toInstant().compareTo(Instant.parse(text));
            }
            if (value instanceof Calendar calendar) {
                return calendar.toInstant().compareTo(Instant.parse(text));
            }
            return 0;
        }
    }

    /**
     * A typed comparison that can replace a Column's value set.
     */
    private enum Comparison {
        /**
         * No comparison. The value set decides.
         */
        NONE,
        /**
         * The text contains the value, ignoring case.
         */
        CONTAINS,
        /**
         * The text starts with the value, ignoring case.
         */
        STARTS,
        /**
         * The text ends with the value, ignoring case.
         */
        ENDS,
        /**
         * The number is greater than the value.
         */
        GREATER,
        /**
         * The number is less than the value.
         */
        LESS,
        /**
         * The date or time is before the value.
         */
        BEFORE,
        /**
         * The date or time is after the value.
         */
        AFTER
    }
}
