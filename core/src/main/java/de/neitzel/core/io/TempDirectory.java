package de.neitzel.core.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * A utility class for creating and managing a temporary directory.
 * Instances of this class create a unique temporary directory on the filesystem
 * that can safely be used during the runtime of a program.
 * <p>
 * The directory is automatically cleaned up when the instance is closed.
 */
public class TempDirectory implements AutoCloseable {

    /**
     * The filesystem path representing the temporary directory managed by this instance.
     * This path is initialized when the TempDirectory object is created and points
     * to a unique, newly created directory.
     * <p>
     * The directory can be used to safely store temporary runtime files. It is automatically
     * deleted along with its content when the associated TempDirectory object is closed.
     */
    private final Path directory;

    /**
     * Creates a temporary directory with a unique name on the filesystem.
     * The directory will have a prefix specified by the user and is intended
     * to serve as a temporary workspace during the runtime of the program.
     *
     * @param prefix the prefix to be used for the name of the temporary directory
     * @throws IOException if an I/O error occurs when creating the directory
     */
    public TempDirectory(String prefix) throws IOException {
        this.directory = Files.createTempDirectory(prefix);
    }

    /**
     * Retrieves the path of the temporary directory associated with this instance.
     *
     * @return the {@code Path} of the temporary directory
     */
    public Path getDirectory() {
        return directory;
    }

    /**
     * Closes the temporary directory by cleaning up its contents and deleting the directory itself.
     * <p>
     * This method ensures that all files and subdirectories within the temporary directory are
     * deleted in a reverse order, starting from the deepest leaf in the directory tree. If
     * the directory does not exist, the method will not perform any actions.
     * <p>
     * If an error occurs while deleting any file or directory, a RuntimeException is thrown
     * with the details of the failure.
     *
     * @throws IOException if an I/O error occurs while accessing the directory or its contents.
     */
    @Override
    public void close() throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> walk = Files.walk(directory)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to delete: " + path, e);
                            }
                        });
            }
        }
    }
}
