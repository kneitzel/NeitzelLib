package de.neitzel.fx.grid.filter;

import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.fxml.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.*;
import java.util.*;

/**
 * FXML controller for literal text, exact numeric, missing/present and explicit membership drafts.
 */
public final class BuiltInFilterEditor implements FilterEditor {
    @FXML private ComboBox<String> mode, operator;
    @FXML private TextField operand, upper, search;
    @FXML private CheckBox caseSensitive;
    @FXML private VBox comparison, checklistPane, candidates;
    @FXML private Label scope, validation, lookupMessage, selectionSummary;
    @FXML private Button selectShown, clearShown, more, retry;
    private Parent view;
    private FilterEditorContext context;
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper("");
    private final Set<FilterValue> selected = new LinkedHashSet<>();
    private boolean missing, closed, configuring, numeric, available;
    private long generation;
    private int offset;
    private FilterCandidates.Context candidateContext;
    private FilterCandidates.Session session;
    private final List<FilterCandidates.Candidate> shown = new ArrayList<>();

    public static BuiltInFilterEditor load(FilterEditorContext context) {
        try {
            var loader =
                    new FXMLLoader(
                            BuiltInFilterEditor.class.getResource("BuiltInFilterEditor.fxml"));
            Parent root = loader.load();
            BuiltInFilterEditor editor = loader.getController();
            editor.view = root;
            try {
                editor.configure(context);
            } catch (RuntimeException error) {
                editor.close();
                throw error;
            }
            return editor;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load built-in filter FXML", error);
        }
    }

    private void configure(FilterEditorContext context) {
        this.context = context;
        configuring = true;
        numeric = context.configuration().editorId().equals("neitzel.number");
        mode.getItems().setAll("Comparison", "Missing", "Present", "Clear filter");
        if (numeric)
            for (var op : NumericFilter.Operator.values()) operator.getItems().add(op.name());
        else for (var op : TextFilter.Operator.values()) operator.getItems().add(op.name());
        operator.setValue("EQUALS");
        mode.setValue("Comparison");
        caseSensitive.setSelected(context.configuration().caseSensitive());
        caseSensitive.setVisible(!numeric);
        caseSensitive.setManaged(!numeric);
        if (context.checklist() != FilterColumn.Checklist.DISABLED) {
            var requestedScope =
                    context.checklist() == FilterColumn.Checklist.CURRENT_PAGE
                            ? FilterCandidates.Scope.CURRENT_PAGE
                            : FilterCandidates.Scope.SOURCE_WIDE;
            candidateContext =
                    new FilterCandidates.Context(
                            context.columnId(), requestedScope, context.otherFilters());
            available = context.configuration().candidates().scopes().contains(requestedScope);
            scope.setText(
                    available
                            ? (requestedScope == FilterCandidates.Scope.CURRENT_PAGE
                                    ? "Current page only — captured when opened"
                                    : "Source-wide — excluding this column's filter")
                            : "Checklist unavailable: provider does not support "
                                    + requestedScope
                                    + ". Use a comparison or supply a capable provider.");
            // Keep the unavailable mode visible with an explanation, never substitute a scope.
            mode.getItems().add("Checklist");
            if (available) {
                try {
                    session = context.configuration().candidates().open(candidateContext);
                } catch (RuntimeException error) {
                    lookupMessage.setText("Lookup failed: " + error.getMessage());
                }
            }
        }
        FilterCondition committed = context.committed();
        if (committed instanceof TextFilter f && !numeric) {
            operator.setValue(f.operator().name());
            operand.setText(f.operand());
            caseSensitive.setSelected(f.caseSensitive());
        } else if (committed instanceof NumericFilter f && numeric) {
            operator.setValue(f.operator().name());
            operand.setText(format(f.operand()));
            if (f.upper() != null) upper.setText(format(f.upper()));
        } else if (committed instanceof MissingFilter f)
            mode.setValue(f.missing() ? "Missing" : "Present");
        else if (committed instanceof ValueFilter f) {
            selected.addAll(f.values());
            missing = f.includeMissing();
            if (!mode.getItems().contains("Checklist"))
                throw new IllegalArgumentException(
                        "Existing checklist requires an enabled candidate provider; clear it"
                                + " explicitly or restore the configuration");
            mode.setValue("Checklist");
        } else if (committed != null)
            throw new IllegalArgumentException("Editor cannot restore " + committed.typeId());
        else mode.setValue("Clear filter");
        validation.textProperty().bind(message);
        mode.valueProperty().addListener((o, a, b) -> changed());
        operator.valueProperty().addListener((o, a, b) -> changed());
        operand.textProperty().addListener((o, a, b) -> changed());
        upper.textProperty().addListener((o, a, b) -> changed());
        caseSensitive.selectedProperty().addListener((o, a, b) -> changed());
        search.textProperty().addListener((o, a, b) -> lookup(false));
        selectShown.setOnAction(e -> selectShown(true));
        clearShown.setOnAction(e -> selectShown(false));
        more.setOnAction(e -> lookup(true));
        retry.setOnAction(e -> lookup(false));
        configuring = false;
        changed();
        if (available) lookup(false);
    }

    private String format(BigDecimal value) {
        return value.toPlainString()
                .replace(
                        '.',
                        DecimalFormatSymbols.getInstance(context.configuration().locale())
                                .getDecimalSeparator());
    }

    private BigDecimal number(String text) {
        // Grouping deliberately disallowed: malformed grouping must never be silently ignored.
        var format =
                (DecimalFormat) NumberFormat.getNumberInstance(context.configuration().locale());
        format.setGroupingUsed(false);
        format.setParseBigDecimal(true);
        String input = text.strip();
        var position = new ParsePosition(0);
        Number parsed = format.parse(input, position);
        if (!(parsed instanceof BigDecimal value) || position.getIndex() != input.length())
            throw new IllegalArgumentException(
                    "Enter a finite number for "
                            + context.configuration().locale()
                            + " without grouping separators");
        var config = context.configuration();
        if (config.integer()) {
            try {
                value.longValueExact();
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException(
                        "Enter a whole number within the signed 64-bit range");
            }
        }
        if ((config.minimum() != null && value.compareTo(config.minimum()) < 0)
                || (config.maximum() != null && value.compareTo(config.maximum()) > 0))
            throw new IllegalArgumentException("Number is outside the configured column range");
        return value;
    }

    public FilterCondition draft() {
        if (closed) throw new IllegalStateException("Editor closed");
        return switch (mode.getValue()) {
            case "Clear filter" -> null;
            case "Missing" -> new MissingFilter(context.columnId(), true);
            case "Present" -> new MissingFilter(context.columnId(), false);
            case "Checklist" -> {
                if (!available)
                    throw new IllegalArgumentException(
                            "Checklist scope is unavailable; configure a capable provider");
                yield new ValueFilter(context.columnId(), selected, missing);
            }
            case "Comparison" ->
                    numeric
                            ? new NumericFilter(
                                    context.columnId(),
                                    NumericFilter.Operator.valueOf(operator.getValue()),
                                    number(operand.getText()),
                                    operator.getValue().equals("RANGE")
                                            ? number(upper.getText())
                                            : null)
                            : new TextFilter(
                                    context.columnId(),
                                    TextFilter.Operator.valueOf(operator.getValue()),
                                    operand.getText(),
                                    caseSensitive.isSelected());
            default -> throw new IllegalArgumentException("Select a filter mode");
        };
    }

    private void changed() {
        if (configuring || closed) return;
        boolean comparing = mode.getValue().equals("Comparison"),
                checklist = mode.getValue().equals("Checklist");
        comparison.setVisible(comparing);
        comparison.setManaged(comparing);
        checklistPane.setVisible(checklist);
        checklistPane.setManaged(checklist);
        boolean range = numeric && "RANGE".equals(operator.getValue());
        upper.setVisible(range);
        upper.setManaged(range);
        selectionSummary.setText(
                selected.size()
                        + " selected values"
                        + (missing ? " plus missing" : "")
                        + " (including values outside this batch)");
        try {
            draft();
            message.set("");
        } catch (RuntimeException error) {
            message.set(error.getMessage());
        }
    }

    private void selectShown(boolean select) {
        for (var value : shown) {
            if (value.value() == null) missing = select;
            else if (select) selected.add(value.value());
            else selected.remove(value.value());
        }
        render();
        changed();
    }

    private void render() {
        candidates.getChildren().clear();
        for (var value : shown) {
            var box = new CheckBox(value.label());
            box.setSelected(value.value() == null ? missing : selected.contains(value.value()));
            box.setOnAction(
                    e -> {
                        if (value.value() == null) missing = box.isSelected();
                        else if (box.isSelected()) selected.add(value.value());
                        else selected.remove(value.value());
                        changed();
                    });
            candidates.getChildren().add(box);
        }
    }

    private void lookup(boolean append) {
        if (closed || !available) return;
        long token = ++generation;
        int requestedOffset = append ? offset : 0;
        lookupMessage.setText("Loading candidates…");
        more.setDisable(true);
        retry.setDisable(true);
        try {
            if (session == null)
                session = context.configuration().candidates().open(candidateContext);
            session.load(
                            new FilterCandidates.Request(
                                    candidateContext, search.getText(), requestedOffset, 50))
                    .whenComplete(
                            (batch, error) ->
                                    Platform.runLater(
                                            () -> {
                                                if (closed || generation != token) return;
                                                retry.setDisable(false);
                                                if (error != null || batch == null) {
                                                    lookupMessage.setText(
                                                            "Lookup failed: "
                                                                    + (error == null
                                                                            ? "missing batch"
                                                                            : error.getMessage())
                                                                    + ". Selections preserved;"
                                                                    + " retry available.");
                                                    return;
                                                }
                                                if (!append) shown.clear();
                                                // Count provider rows, not de-duplicated display
                                                // rows, for the next offset.
                                                offset =
                                                        Math.addExact(
                                                                requestedOffset,
                                                                batch.candidates().size());
                                                for (var candidate : batch.candidates())
                                                    if (shown.stream()
                                                            .noneMatch(
                                                                    c ->
                                                                            Objects.equals(
                                                                                    c.value(),
                                                                                    candidate
                                                                                            .value())))
                                                        shown.add(candidate);
                                                more.setDisable(
                                                        !batch.hasMore()
                                                                || batch.candidates().isEmpty());
                                                lookupMessage.setText(
                                                        batch.hasMore()
                                                                ? "More candidates available"
                                                                : "End of candidate results");
                                                render();
                                                changed();
                                            }));
        } catch (RuntimeException error) {
            retry.setDisable(false);
            lookupMessage.setText(
                    "Lookup failed: " + error.getMessage() + ". Selections preserved.");
        }
    }

    public Parent view() {
        return view;
    }

    public ReadOnlyStringProperty validationMessageProperty() {
        return message.getReadOnlyProperty();
    }

    public boolean nestedPopupShowing() {
        return mode.isShowing() || operator.isShowing();
    }

    public void closeNestedPopup() {
        mode.hide();
        operator.hide();
    }

    public void close() {
        if (closed) return;
        closed = true;
        generation++;
        closeNestedPopup();
        validation.textProperty().unbind();
        if (session != null) {
            var retired = session;
            session = null;
            retired.close();
        }
    }
}
