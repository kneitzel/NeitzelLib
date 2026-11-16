package de.neitzel.fx.docking;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

/** Captures application wording and keeps presentation separate from diagnostic failures. */
final class DockMessages {
    private final ResourceBundle defaults = ResourceBundle.getBundle("de.neitzel.fx.docking.Docking", Locale.ROOT);
    private final Map<String, String> overrides;
    private final Locale locale;

    DockMessages(ResourceBundle resources) {
        Map<String, String> captured = new HashMap<>();
        if (resources != null) {
            for (String key : resources.keySet()) {
                if (resources.getObject(key) instanceof String text) captured.put(key, text);
            }
        }
        overrides = Map.copyOf(captured);
        locale = resources == null || resources.getLocale() == null ? Locale.ROOT : resources.getLocale();
    }

    String text(String key) {
        if (overrides.containsKey(key)) return overrides.get(key);
        return defaults.containsKey(key) ? defaults.getString(key) : defaults.getString("dock.failure.generic");
    }

    String format(String key, Object... values) {
        return new MessageFormat(text(key), locale).format(values);
    }

    String failure(DockFailure failure) {
        Objects.requireNonNull(failure, "failure");
        String codeKey = "dock.failure." + failure.code().name();
        String applicationKey = failure.messageKey();
        if (applicationKey != null && !applicationKey.isBlank()) {
            return overrides.getOrDefault(applicationKey, text(codeKey));
        }
        if (overrides.containsKey(codeKey)) return overrides.get(codeKey);
        // Keep the existing direct-message API useful when no localization key was supplied.
        return failure.message() == null || failure.message().isBlank() ? text(codeKey) : failure.message();
    }
}
