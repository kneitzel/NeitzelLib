package de.neitzel.fx.i18n;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

/** A live ResourceBundle for FXML, controller injection and immediate Java lookups. */
public final class Messages extends ResourceBundle {
    private static final Control RULES = Control.getControl(Control.FORMAT_PROPERTIES);
    private final Localizer localizer;
    private final List<Localizer.Family> families;
    private Locale loaded;
    private Map<String,String> entries;

    Messages(Localizer localizer, List<Localizer.Family> families) {
        this.localizer = localizer;
        this.families = families;
    }

    /** Opt-in live label for JavaFX property binding. */
    public javafx.beans.value.ObservableValue<String> observable(String key) {
        return localizer.localeProperty().map(locale -> text(key));
    }

    public String text(String key, Object... arguments) {
        String pattern = getString(key);
        return arguments.length == 0 ? pattern : new MessageFormat(pattern, getLocale()).format(arguments);
    }

    public Localizer localizer() { return localizer; }
    @Override public Locale getLocale() { return localizer.getLocale(); }

    @Override protected Object handleGetObject(String key) {
        String value = values().get(Objects.requireNonNull(key));
        if (value != null) return value;
        localizer.reportMissing(families, getLocale(), key);
        return "⟦" + key + "⟧";
    }

    /** FXMLLoader checks containsKey before getString. Every key has a visible fallback. */
    @Override public boolean containsKey(String key) { Objects.requireNonNull(key); return true; }
    public boolean defines(String key) { return values().containsKey(key); }
    @Override public Enumeration<String> getKeys() { return Collections.enumeration(values().keySet()); }
    @Override protected Set<String> handleKeySet() { return values().keySet(); }

    private synchronized Map<String,String> values() {
        Locale current = getLocale();
        if (current.equals(loaded)) return entries;
        var languages = new LinkedHashSet<>(RULES.getCandidateLocales("", current));
        languages.addAll(RULES.getCandidateLocales("", localizer.fallback));
        languages.remove(Locale.ROOT);
        languages.add(Locale.ROOT);
        var result = new HashMap<String,String>();
        for (Locale language : languages) {
            for (var family : families) read(family, language, result);
        }
        entries = Map.copyOf(result);
        loaded = current;
        return entries;
    }

    private static void read(Localizer.Family family, Locale locale, Map<String,String> result) {
        String file = RULES.toBundleName(family.path(), locale) + ".properties";
        try {
            var url = family.siblingOf() == null ? family.anchor().getResource(file)
                    : new java.net.URL(family.siblingOf(), file);
            if (url == null) return;
            try (var reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
                var properties = new Properties();
                properties.load(reader);
                for (var key : properties.stringPropertyNames()) result.putIfAbsent(key, properties.getProperty(key));
            }
        } catch (FileNotFoundException missingSibling) {
            // A locale or layer may have no properties file.
        } catch (IOException problem) {
            throw new UncheckedIOException("Cannot read catalog " + file, problem);
        }
    }
}
