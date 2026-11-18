package de.neitzel.gson.grid;

import de.neitzel.core.config.ApplicationDirectories;
import de.neitzel.grid.GridApplicationConfiguration;

import java.nio.file.Path;

/** Simple opt-in factory; constructing it does not create directories or read preferences. */
public final class GridSetupFiles {
    private GridSetupFiles() {}

    public static GridApplicationConfiguration forApplication(String application) {
        return forApplication(
                application,
                ApplicationDirectories.configurationDirectory(application).resolve("grids"));
    }

    public static GridApplicationConfiguration forApplication(
            String application, Path gridDirectory) {
        return forApplication(application, gridDirectory, new GridSetupJson());
    }

    public static GridApplicationConfiguration forApplication(
            String application, Path gridDirectory, GridSetupJson codecs) {
        return new GridApplicationConfiguration(
                application, new JsonGridSetupStore(gridDirectory, codecs));
    }
}
