package de.neitzel.core.config;

import java.nio.file.Path;
import java.util.Map;

/** Native per-user application directories. Resolution creates no files. */
public final class ApplicationDirectories {
    private ApplicationDirectories() {}

    public static Path configurationDirectory(String application) {
        return configurationDirectory(application, System.getProperty("os.name"),
                Path.of(System.getProperty("user.home")), System.getenv());
    }

    /** Explicit platform inputs support launchers and isolated environments. */
    public static Path configurationDirectory(String application, String operatingSystem,
                                              Path home, Map<String, String> environment) {
        if (application == null || application.isBlank() || application.equals(".") || application.equals("..")
                || application.indexOf('/') >= 0 || application.indexOf('\\') >= 0 || application.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Application name must be one directory name");
        }
        String os = operatingSystem.toLowerCase(java.util.Locale.ROOT);
        Path base;
        if (os.startsWith("mac") || os.equals("darwin")) {
            base = home.resolve("Library/Application Support");
        } else if (os.startsWith("windows")) {
            base = absoluteOverride(environment.get("APPDATA"), home.resolve("AppData/Roaming"));
        } else {
            base = absoluteOverride(environment.get("XDG_CONFIG_HOME"), home.resolve(".config"));
        }
        return base.resolve(application);
    }

    private static Path absoluteOverride(String override, Path fallback) {
        if (override == null || override.isBlank()) return fallback;
        Path path = Path.of(override);
        return path.isAbsolute() ? path : fallback;
    }
}
