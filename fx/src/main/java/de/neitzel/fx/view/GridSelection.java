package de.neitzel.fx.view;

import java.util.List;
import java.util.Objects;

/**
 * One published selection of a grid. Keys retain their selection order and can include keys outside
 * the current page. Elements follow the grid's display order and contain only available selected
 * rows. The lead element is null when the lead key has no available row.
 *
 * @param selectedKeys selected keys, including retained off-page keys
 * @param selectedElements selected models of visible and interactive rows
 * @param leadKey lead key, or null for an empty selection
 * @param leadElement lead model, or null when unavailable
 * @param <K> row key type
 * @param <T> row model type
 */
public record GridSelection<K, T>(
        List<K> selectedKeys, List<T> selectedElements, K leadKey, T leadElement) {

    /** Copies both lists so a published selection remains stable as the grid changes. */
    public GridSelection {
        selectedKeys = List.copyOf(selectedKeys);
        selectedElements = List.copyOf(selectedElements);
    }

    static <K, T> GridSelection<K, T> empty() {
        return new GridSelection<>(List.of(), List.of(), null, null);
    }

    /** A refreshed row can replace an equal model with a different instance. */
    boolean sameSelection(GridSelection<K, T> previous) {
        if (!selectedKeys.equals(previous.selectedKeys) || !Objects.equals(leadKey, previous.leadKey)
                || leadElement != previous.leadElement
                || selectedElements.size() != previous.selectedElements.size()) {
            return false;
        }
        for (int index = 0; index < selectedElements.size(); index++) {
            if (selectedElements.get(index) != previous.selectedElements.get(index)) {
                return false;
            }
        }
        return true;
    }
}
