package de.neitzel.fx.theme;

import java.util.*;

/** A named CSS bundle. Themes contain presentation only; applications own preference storage. */
public record Theme(String id, List<String> stylesheets) {
    public Theme {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Missing theme ID");
        stylesheets = List.copyOf(stylesheets);
        if (stylesheets.isEmpty()) throw new IllegalArgumentException("Missing theme stylesheets");
    }

    public static Theme light() {
        return bundled("light");
    }

    public static Theme dark() {
        return bundled("dark");
    }

    public static Theme highContrast() {
        return bundled("high-contrast");
    }

    private static Theme bundled(String id) {
        return new Theme(id, List.of(resource("theme-base.css"), resource(id + ".css")));
    }

    private static String resource(String name) {
        return Objects.requireNonNull(
                        Theme.class.getResource(name), "Missing bundled theme: " + name)
                .toExternalForm();
    }
}
