package de.neitzel.gson.grid;

import de.neitzel.grid.*;

import java.nio.file.Path;

/** Separate-process CAS probe, deliberately independent of JUnit scheduling. */
public final class GridStoreRace {
    public static void main(String[] args) throws Exception {
        var store = new JsonGridSetupStore(Path.of(args[0]), new GridSetupJson());
        try {
            store.save(new GridSetupStore.Key("records", null), args[1], GridFileStoreTest.setup());
        } catch (GridSetupStore.Conflict expected) {
            System.exit(3);
        }
    }
}
