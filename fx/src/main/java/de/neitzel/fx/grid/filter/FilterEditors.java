package de.neitzel.fx.grid.filter;

import de.neitzel.grid.*;

import java.util.*;
import java.util.function.Function;

/** Editor availability never implies evaluation, persistence or adapter support. */
public final class FilterEditors {
    public record Registration(
            String id,
            FilterCapabilities capabilities,
            Set<String> conditionTypes,
            Function<FilterEditorContext, FilterEditor> factory) {
        public Registration {
            if (id == null || id.isBlank())
                throw new IllegalArgumentException("Missing stable editor ID");
            Objects.requireNonNull(capabilities);
            conditionTypes = Set.copyOf(conditionTypes);
            if (conditionTypes.isEmpty())
                throw new IllegalArgumentException("Declare emitted condition types");
            Objects.requireNonNull(factory);
        }

        public void requireEvaluation(String executionId) {
            boolean supported =
                    executionId.equals("local")
                            ? capabilities.localEvaluation()
                            : capabilities.adapters().contains(executionId);
            if (!supported)
                throw new UnsupportedOperationException(
                        "Editor "
                                + id
                                + " does not declare evaluation for "
                                + executionId
                                + "; register a compatible filter or change source");
        }

        public void validate(FilterCondition condition) {
            if (condition != null && !conditionTypes.contains(condition.typeId()))
                throw new UnsupportedOperationException(
                        "Editor " + id + " did not declare condition " + condition.typeId());
        }
    }

    private final Map<String, Registration> registrations = new LinkedHashMap<>();

    public void register(Registration registration) {
        if (registrations.putIfAbsent(registration.id(), registration) != null)
            throw new IllegalArgumentException(
                    "Duplicate editor registration: " + registration.id());
    }

    public Registration require(String id) {
        var registration = registrations.get(id);
        if (registration == null)
            throw new IllegalArgumentException("Register the missing filter editor: " + id);
        return registration;
    }

    public static FilterEditors builtIns() {
        return builtIns(Set.of());
    }

    /** Application-declared adapter support; every concrete condition is still source-validated. */
    public static FilterEditors builtIns(Set<String> adapters) {
        var editors = new FilterEditors();
        for (String id : List.of("neitzel.text", "neitzel.number"))
            editors.register(
                    new Registration(
                            id,
                            new FilterCapabilities(true, true, adapters),
                            Set.of(id, "neitzel.missing", "neitzel.values"),
                            BuiltInFilterEditor::load));
        return editors;
    }
}
