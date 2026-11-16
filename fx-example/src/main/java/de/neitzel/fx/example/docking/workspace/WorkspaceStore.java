package de.neitzel.fx.example.docking.workspace;

import lombok.Getter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

/** Application-owned storage used by the reference workspace; it is not a library serialization API. */
public final class WorkspaceStore {
    private static final com.google.gson.Gson JSON = WorkspaceJson.create();
    @Getter private final Path file;

    public WorkspaceStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (this.file.getFileName() == null) throw new IllegalArgumentException("A workspace file name is required");
    }

    public Optional<StoredWorkspace> read() throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        try (var input = Files.newBufferedReader(file)) {
            StoredWorkspace workspace = JSON.fromJson(input, StoredWorkspace.class);
            if (workspace == null) throw new IOException("Workspace file contains no workspace");
            requireSupportedVersion(workspace);
            return Optional.of(workspace);
        } catch (RuntimeException invalid) {
            throw new IOException("Cannot read workspace data", invalid);
        }
    }

    public void save(StoredWorkspace workspace) throws IOException {
        Objects.requireNonNull(workspace, "workspace");
        requireSupportedVersion(workspace);
        String json;
        try { json = JSON.toJson(workspace); }
        catch (RuntimeException invalid) { throw new IOException("Cannot encode workspace data", invalid); }
        Files.createDirectories(file.getParent());
        Path prepared = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".pending");
        try {
            Files.writeString(prepared, json);
            try {
                Files.move(prepared, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(prepared, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            try { Files.deleteIfExists(prepared); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void requireSupportedVersion(StoredWorkspace workspace) throws IOException {
        if (workspace.version() != StoredWorkspace.CURRENT_VERSION) {
            throw new IOException("Unsupported application workspace version: " + workspace.version());
        }
    }
}
