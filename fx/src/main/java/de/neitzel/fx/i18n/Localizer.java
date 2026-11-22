package de.neitzel.fx.i18n;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Application-wide language selection and ordered property catalog sources. */
public final class Localizer {
    public record Catalog(Class<?> anchor, String name, String packageRoot) {
        public Catalog {
            Objects.requireNonNull(anchor);
            Objects.requireNonNull(packageRoot);
            if (!name.matches("[\\w.-]+")) throw new IllegalArgumentException("Invalid catalog name: " + name);
        }

        public Catalog(Class<?> anchor, String name) {
            this(anchor, name, anchor.getPackageName());
        }
    }

    record Family(Class<?> anchor, String path, URL siblingOf) {}
    private record Missing(List<Family> context, Locale locale, String key) {}
    private final Set<Missing> missing = ConcurrentHashMap.newKeySet();
    private final ReadOnlyObjectWrapper<Locale> locale;
    final Locale fallback;
    private final List<Catalog> catalogs;

    public Localizer(Locale initial, Locale fallback, Catalog application, Catalog... libraries) {
        this.locale = new ReadOnlyObjectWrapper<>(initial == null ? Locale.getDefault(Locale.Category.DISPLAY) : initial);
        this.fallback = Objects.requireNonNull(fallback);
        var sources = new ArrayList<Catalog>();
        sources.add(Objects.requireNonNull(application));
        sources.addAll(List.of(libraries));
        this.catalogs = List.copyOf(sources);
    }

    public Locale getLocale() { return locale.get(); }
    public ReadOnlyObjectProperty<Locale> localeProperty() { return locale.getReadOnlyProperty(); }

    /** Set on the JavaFX thread when controls observe this property. */
    public void setLocale(Locale value) {
        if (!getLocale().equals(Objects.requireNonNull(value))) locale.set(value);
    }

    public Messages forClass(Class<?> type) { return forView(null, type); }

    public Messages forView(URL fxml, Class<?> controller) {
        var families = new LinkedHashSet<Family>();
        Class<?> anchor = controller == null ? catalogs.getFirst().anchor() : controller;
        if (fxml != null) {
            String path = fxml.getPath();
            String file = path.substring(path.lastIndexOf('/') + 1);
            families.add(new Family(anchor, file.replaceFirst("\\.fxml$", ""), fxml));
        }
        if (controller != null) families.add(new Family(controller, controller.getSimpleName(), null));
        String pkg = anchor.getPackageName();
        String root = catalogs.stream().map(Catalog::packageRoot)
                .filter(p -> pkg.equals(p) || pkg.startsWith(p + "."))
                .max(java.util.Comparator.comparingInt(String::length)).orElse(pkg);
        if (fxml != null) {
            String directory = fxml.getPath().substring(0, fxml.getPath().lastIndexOf('/') + 1);
            catalogs.stream().filter(c -> !c.packageRoot().isEmpty()
                            && directory.contains("/" + c.packageRoot().replace('.', '/') + "/"))
                    .max(java.util.Comparator.comparingInt(c -> c.packageRoot().length()))
                    .ifPresent(source -> {
                        String prefix = "/" + source.packageRoot().replace('.', '/') + "/";
                        String path = directory.substring(directory.lastIndexOf(prefix), directory.length() - 1);
                        addPackages(families, source.anchor(), path, source.packageRoot());
                    });
        }
        addPackages(families, anchor, "/" + pkg.replace('.', '/'), root);
        for (Catalog catalog : catalogs) families.add(new Family(catalog.anchor(), "/" + catalog.name(), null));
        return new Messages(this, List.copyOf(families));
    }

    private static void addPackages(Set<Family> result, Class<?> anchor, String path, String root) {
        String boundary = "/" + root.replace('.', '/');
        while (path.length() > 1) {
            result.add(new Family(anchor, path + "/Messages", null));
            if (path.equals(boundary)) break;
            path = path.substring(0, path.lastIndexOf('/'));
        }
    }

    void reportMissing(List<Family> context, Locale language, String key) {
        if (missing.add(new Missing(context, language, key))) {
            System.getLogger(Localizer.class.getName()).log(System.Logger.Level.WARNING,
                    "Missing translation {0} for {1} in {2}", key, language, context);
        }
    }
}
