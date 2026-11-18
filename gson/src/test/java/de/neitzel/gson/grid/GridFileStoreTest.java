package de.neitzel.gson.grid;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.grid.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

class GridFileStoreTest {
    @TempDir Path directory;

    static GridSetup setup() {
        return new GridSetup(
                2,
                "records",
                1,
                List.of(new GridSetup.ColumnState("type", "text", new ColumnWidth.Default())),
                List.of(),
                List.of(),
                new PageRequest.Paged(0, 50),
                new GroupingSetup(
                        List.of("type"),
                        List.of(
                                new GroupingSetup.Choice(
                                        new GroupPath(List.of(new GroupPath.Segment("type", null))),
                                        true))));
    }

    @Test
    void strictVersionTwoRoundTripKeepsTypedMissingAndRejectsMalformedNewFormat() {
        var codec = new GridSetupJson();
        String json = codec.encode(setup());
        assertEquals(setup(), codec.decode(json));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(json.replace("\"expanded\": true", "\"expanded\": \"true\"")));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(json.replace("\"choices\"", "\"unknown\"")));
    }

    @Test
    void noFilesUntilSaveAndSafeIdentityDoesNotTreatKeysAsPaths() throws Exception {
        var root = directory.resolve("not-created");
        var store = new JsonGridSetupStore(root, new GridSetupJson());
        var key = new GridSetupStore.Key("../../records", "../outside");
        assertNull(store.load(key).setup());
        assertFalse(Files.exists(root));
        String revision = store.save(key, "absent", setup());
        assertEquals(root, store.path(key).getParent());
        assertEquals(setup(), store.load(key).setup());
        assertThrows(GridSetupStore.Conflict.class, () -> store.save(key, "absent", setup()));
        assertEquals(revision, store.revision(key));
        assertNotEquals(
                store.path(new GridSetupStore.Key("a", null)),
                store.path(new GridSetupStore.Key("a", "null")));
    }

    @Test
    void competingProcessesCannotBothReplaceTheSameRevision() throws Exception {
        var store = new JsonGridSetupStore(directory, new GridSetupJson());
        var key = new GridSetupStore.Key("records", null);
        String revision = store.save(key, "absent", setup());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String cp = System.getProperty("java.class.path");
        var first =
                new ProcessBuilder(
                                java,
                                "-cp",
                                cp,
                                GridStoreRace.class.getName(),
                                directory.toString(),
                                revision)
                        .redirectErrorStream(true)
                        .start();
        var second =
                new ProcessBuilder(
                                java,
                                "-cp",
                                cp,
                                GridStoreRace.class.getName(),
                                directory.toString(),
                                revision)
                        .redirectErrorStream(true)
                        .start();
        assertTrue(first.waitFor(15, TimeUnit.SECONDS));
        assertTrue(second.waitFor(15, TimeUnit.SECONDS));
        var exits = new ArrayList<>(List.of(first.exitValue(), second.exitValue()));
        Collections.sort(exits);
        assertEquals(
                List.of(0, 3),
                exits,
                new String(first.getInputStream().readAllBytes())
                        + new String(second.getInputStream().readAllBytes()));
        assertEquals(setup(), store.load(key).setup());
    }
}
