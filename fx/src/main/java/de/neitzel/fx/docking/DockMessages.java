package de.neitzel.fx.docking;

import de.neitzel.fx.i18n.Messages;
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
    private final Messages live;

    DockMessages(ResourceBundle resources) {
        Map<String, String> captured = new HashMap<>();
        live = resources instanceof Messages messages ? messages : null;
        if (resources != null && live == null) {
            for (String key : resources.keySet()) {
                if (resources.getObject(key) instanceof String text) captured.put(key, text);
            }
        }
        overrides = Map.copyOf(captured);
        locale = resources == null || resources.getLocale() == null ? Locale.ROOT : resources.getLocale();
    }

    private String override(String key) {
        return live != null && live.defines(key) ? live.text(key) : overrides.get(key);
    }

    String text(String key) {
        String wording = override(key);
        if (wording != null) return wording;
        return defaults.containsKey(key) ? defaults.getString(key) : defaults.getString("dock.failure.generic");
    }

    String format(String key, Object... values) {
        return new MessageFormat(text(key), live == null ? locale : live.getLocale()).format(values);
    }

    String failure(DockFailure failure) {
        Objects.requireNonNull(failure, "failure");
        String codeKey = "dock.failure." + failure.code().name();
        String applicationKey = failure.messageKey();
        if (applicationKey != null && !applicationKey.isBlank()) {
            return Objects.requireNonNullElseGet(override(applicationKey), () -> text(codeKey));
        }
        String wording = override(codeKey);
        if (wording != null) return wording;
        // Keep the existing direct-message API useful when no localization key was supplied.
        return failure.message() == null || failure.message().isBlank() ? text(codeKey) : failure.message();
    }
}
