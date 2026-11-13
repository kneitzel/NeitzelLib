package de.neitzel.fx.view;

import java.util.*;

/** Resolves declarative services without constructing providers or Models. */
public final class ViewModelDefaults {
    private ViewModelDefaults() {}

    public static Class<?> select(Class<?> model, Class<?> explicit, ClassLoader loader) {
        var entries = new HashMap<Class<?>, ViewModelRegistration>();
        var origins = new HashMap<Class<?>, String>();
        for (var provider : ServiceLoader.load(ViewModelRegistry.class, loader).stream().toList()) {
            Class<?> type = provider.type();
            for (var entry : type.getAnnotationsByType(ViewModelRegistration.class)) {
                if (!entry.generated().isAssignableFrom(entry.defaultType())) {
                    throw new IllegalArgumentException(
                            "Default must extend generated ViewModel: " + type.getName());
                }
                var old = entries.putIfAbsent(entry.model(), entry);
                if (old != null
                        && (old.generated() != entry.generated()
                                || old.defaultType() != entry.defaultType())) {
                    throw new IllegalArgumentException(
                            "Conflicting ViewModel defaults for "
                                    + entry.model().getName()
                                    + " in "
                                    + origins.get(entry.model())
                                    + " and "
                                    + type.getName());
                }
                origins.putIfAbsent(entry.model(), type.getName());
            }
        }
        var candidates =
                entries.values().stream().filter(e -> e.model().isAssignableFrom(model)).toList();
        var best =
                candidates.stream()
                        .filter(
                                e ->
                                        candidates.stream()
                                                .noneMatch(
                                                        other ->
                                                                other != e
                                                                        && e.model()
                                                                                .isAssignableFrom(
                                                                                        other
                                                                                                .model())))
                        .toList();
        if (best.size() > 1)
            throw new IllegalArgumentException(
                    "Ambiguous ViewModel defaults for " + model.getName());
        var chosen = best.isEmpty() ? null : best.getFirst();
        if (explicit != null && explicit != ViewModel.class) {
            if (chosen != null && !chosen.generated().isAssignableFrom(explicit)) {
                throw new IllegalArgumentException(
                        "Explicit ViewModel "
                                + explicit.getName()
                                + " must extend "
                                + chosen.generated().getName());
            }
            return explicit;
        }
        return chosen == null ? ViewModel.class : chosen.defaultType();
    }
}
