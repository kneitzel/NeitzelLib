package de.neitzel.fx.view.selection;

import de.neitzel.fx.view.DataGridSelection;
import de.neitzel.fx.view.ViewModelClass;
import jakarta.inject.Singleton;
import javafx.beans.value.ObservableValue;

/** Supplies a read-only summary of the selected keys and available rows. */
@Singleton
@ViewModelClass(SelectionSummaryController.Summary.class)
public class SelectionSummaryController {

    public static class Summary {
        private final ObservableValue<String> summary;

        public Summary(DataGridSelection<?, ?> selection) {
            summary = selection.selectionProperty().map(state ->
                    state.selectedKeys() + " · " + state.selectedElements().size() + " visible");
        }

        public ObservableValue<String> summaryProperty() {
            return summary;
        }
    }
}
