# Native application configuration directories

`ApplicationDirectories.configurationDirectory(application)` resolves a per-user configuration directory without creating files. The application must be a single nonempty directory name, with no separators or traversal components.

- macOS: `~/Library/Application Support/<application>`.
- Windows: `%APPDATA%/<application>`, falling back to `~/AppData/Roaming/<application>` when the environment override is absent or relative.
- Linux: `$XDG_CONFIG_HOME/<application>` when absolute, otherwise `~/.config/<application>`.

The overload accepting an operating-system name, home directory, and environment map supports isolated launchers and deterministic checks. Resolution never reads or writes an application's preferences. The consuming application owns its schema, persistence, validation, and UI.

Tests cover macOS Application Support, Windows roaming paths, Linux XDG overrides/fallback, and invalid application names. Simulated platform inputs do not establish native packaged-application acceptance on those platforms.
