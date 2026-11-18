package de.neitzel.gson.grid;

import com.google.gson.*;
import com.google.gson.stream.*;

import de.neitzel.grid.*;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Optional file store: safe opaque identities, strict JSON, locked compare-and-atomic-replace. */
public final class JsonGridSetupStore implements GridSetupStore {
    private static final ReentrantLock[] JVM_LOCKS = new ReentrantLock[64];

    static {
        Arrays.setAll(JVM_LOCKS, i -> new ReentrantLock());
    }

    private final Path directory;
    private final GridSetupJson json;

    public JsonGridSetupStore(Path directory, GridSetupJson json) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        this.json = Objects.requireNonNull(json);
    }

    public Path path(Key key) {
        String identity =
                key.gridId().length()
                        + ":"
                        + key.gridId()
                        + (key.context() == null
                                ? "N"
                                : "S" + key.context().length() + ":" + key.context());
        return directory.resolve(hash(identity.getBytes(StandardCharsets.UTF_8)) + ".json");
    }

    @Override
    public Stored load(Key key) throws IOException {
        Path path = path(key);
        if (!Files.exists(path)) return new Stored(null, "absent");
        byte[] bytes = Files.readAllBytes(path);
        try (var reader =
                new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            reader.setStrictness(Strictness.STRICT);
            reader.setNestingLimit(80);
            JsonObject root = GridSetupJson.object(GridSetupJson.read(reader));
            if (reader.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Trailing setup document");
            GridSetupJson.keys(root, "gridId", "context", "revision", "setup");
            GridSetupJson.string(root, "revision");
            String context =
                    root.get("context").isJsonNull() ? null : GridSetupJson.string(root, "context");
            if (!key.gridId().equals(GridSetupJson.string(root, "gridId"))
                    || !Objects.equals(key.context(), context))
                throw new IllegalArgumentException("Stored grid/context identity differs");
            return new Stored(json.decode(root.get("setup").toString()), hash(bytes));
        }
    }

    @Override
    public String revision(Key key) throws IOException {
        Path path = path(key);
        return Files.exists(path) ? hash(Files.readAllBytes(path)) : "absent";
    }

    @Override
    public String save(Key key, String expected, GridSetup setup) throws IOException {
        // Encode fully before touching the existing document or acquiring its lock.
        var root = new JsonObject();
        root.addProperty("gridId", key.gridId());
        root.addProperty("context", key.context());
        root.addProperty("revision", UUID.randomUUID().toString());
        root.add("setup", JsonParser.parseString(json.encode(setup)));
        byte[] bytes =
                new GsonBuilder()
                        .setPrettyPrinting()
                        .serializeNulls()
                        .create()
                        .toJson(root)
                        .getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(directory);
        Path path = path(key), lock = path.resolveSibling(path.getFileName() + ".lock");
        ReentrantLock local = JVM_LOCKS[Math.floorMod(path.hashCode(), JVM_LOCKS.length)];
        local.lock();
        try (var channel =
                        FileChannel.open(
                                lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var ignored = channel.lock()) {
            if (!Objects.equals(expected, revision(key))) throw new GridSetupStore.Conflict();
            Path temporary = Files.createTempFile(directory, ".grid-setup-", ".tmp");
            try {
                try (var output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    var buffer = java.nio.ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) output.write(buffer);
                    output.force(true);
                }
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return hash(bytes);
        } finally {
            local.unlock();
        }
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
