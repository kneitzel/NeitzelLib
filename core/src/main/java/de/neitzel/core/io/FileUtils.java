package de.neitzel.core.io;

import de.neitzel.core.util.ArrayUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.stream.Collectors;

/**
 * A utility class for file-related operations. This class provides functionalities
 * for handling files and directories in an efficient manner.
 */
@Slf4j
public class FileUtils {
    /**
     * Defines a standardized timestamp format for debugging purposes, specifically used for naming
     * or identifying files with precise timestamps. The format is "yyyy-MM-dd_HH_mm_ss_SSS", which
     * includes:
     * - Year in four digits (yyyy)
     * - Month in two digits (MM)
     * - Day of the month in two digits (dd)
     * - Hour in 24-hour format with two digits (HH)
     * - Minutes in two digits (mm)
     * - Seconds in two digits (ss)
     * - Milliseconds in three digits (SSS)
     * <p>
     * This ensures timestamps are sortable and easily identifiable.
     */
    public static final SimpleDateFormat DEBUG_FILE_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss_SSS");

    /**
     * Default encoding used for string checks and validations in the application.
     * <p>
     * This constant represents the `ISO-8859-15` encoding, which is a standardized
     * character set encoding, commonly used in contexts where backward compatibility
     * with `ISO-8859-1` is required, but with support for certain additional characters,
     * such as the euro currency symbol (€).
     */
    public static final String DEFAULT_CHECK_ENCODING = "ISO-8859-15";

    /**
     * Specifies the default buffer size used for data processing operations.
     * <p>
     * This constant represents the size of the buffer in bytes, set to 1024,
     * and is typically utilized in input/output operations to optimize performance
     * by reducing the number of read/write calls.
     */
    public static final int BUFFER_SIZE = 1024;

    /**
     * Private constructor to prevent instantiation of the utility class.
     * This utility class is not meant to be instantiated, as it only provides
     * static utility methods for array-related operations.
     *
     * @throws UnsupportedOperationException always, to indicate that this class
     *                                       should not be instantiated.
     */
    private FileUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Determines if the content of the given file is encoded in UTF-8.
     *
     * @param file The file to check for UTF-8 encoding. Must not be null.
     * @return true if the file content is in UTF-8 encoding; false otherwise.
     * @throws IOException If an I/O error occurs while reading the file.
     */
    public static boolean isUTF8(final File file) throws IOException {
        return isUTF8(file, DEFAULT_CHECK_ENCODING);
    }

    /**
     * Determines whether the given file is encoded in UTF-8.
     *
     * @param file          The file to be checked for UTF-8 encoding.
     * @param checkEncoding The character encoding to use while checking the file content.
     * @return true if the file is determined to be encoded in UTF-8; false otherwise.
     * @throws IOException If an I/O error occurs while reading the file.
     */
    public static boolean isUTF8(final File file, final String checkEncoding) throws IOException {

        if (hasUTF8BOM(file)) return true;

        int BUFFER_SIZE = 1024;
        char[] buffer = new char[BUFFER_SIZE];

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), checkEncoding)) {

            while (reader.read(buffer, 0, BUFFER_SIZE) > 0) {

                if (
                        (ArrayUtils.contains(buffer, (char) 0x00C2)) // Part of UTF-8 Characters 0xC2 0xZZ
                                || (ArrayUtils.contains(buffer, (char) 0x00C3))) { // Part of UTF-8 Characters 0xC3 0xZZ
                    return true;
                }

            }

            return false;
        } catch (IOException ex) {
            log.error("Exception while reading file.", ex);
            throw ex;
        }
    }

    /**
     * Checks if the provided file starts with a UTF-8 Byte Order Mark (BOM).
     * <p>
     * This method reads the first character of the file using a reader that assumes
     * UTF-8 encoding and checks if it matches the Unicode Byte Order Mark (U+FEFF).
     *
     * @param file The file to check for a UTF-8 BOM. Must not be null.
     * @return true if the file starts with a UTF-8 BOM, false otherwise.
     * @throws IOException If an input or output exception occurs while reading the file.
     */
    public static boolean hasUTF8BOM(final File file) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return reader.read() == 0xFEFF;
        } catch (IOException ex) {
            log.error("Exception while reading file.", ex);
            throw ex;
        }
    }

    /**
     * Converts the content of a text file from one character encoding format to another.
     * <p>
     * This method reads the input text file using the specified source encoding and writes
     * the content to the output text file in the specified target encoding.
     *
     * @param inFile       The input text file to be converted. Must not be null.
     * @param sourceFormat The character encoding of the input file. Must not be null or empty.
     * @param outFile      The output text file to write the converted content to. Must not be null.
     * @param targetFormat The character encoding to be used for the output file. Must not be null or empty.
     * @throws IOException If an I/O error occurs during reading or writing.
     */
    public static void convertTextFile(final File inFile, final String sourceFormat, final File outFile, final String targetFormat) throws IOException {
        char[] buffer = new char[BUFFER_SIZE];

        int charsRead, startIndex;
        boolean first = true;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(inFile), sourceFormat);
             OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outFile), targetFormat)) {

            while ((charsRead = reader.read(buffer, 0, BUFFER_SIZE)) > 0) {
                startIndex = 0;

                if (first) {
                    // Check UTF-8 BOM
                    if (buffer[0] == 0xFEFF) {
                        log.info("BOM found!");
                        startIndex = 1;
                    }

                    first = false;
                }

                writer.write(buffer, startIndex, charsRead - startIndex);
            }

        } catch (IOException ex) {
            log.error("Exception when converting Textfile.", ex);
            throw ex;
        }
    }

    /**
     * Creates a universal file reader for the specified file and format.
     * The method resolves the file using its name and the expected format,
     * returning an InputStreamReader for reading the file contents.
     *
     * @param filename       the name of the file to be read.
     * @param expectedFormat the format expected for the file content.
     * @return an InputStreamReader for the specified file and format.
     * @throws IOException if an I/O error occurs while opening or reading the file.
     */
    public static InputStreamReader createUniversalFileReader(final String filename, final String expectedFormat) throws IOException {
        return createUniversalFileReader(new File(filename), expectedFormat);
    }

    /**
     * Creates a universal file reader for the specified file with an expected format.
     * This method wraps the given file in an InputStreamReader for consistent character stream manipulation.
     *
     * @param file           The file to be read. Must not be null.
     * @param expectedFormat The expected format of the file (e.g., encoding). Must not be null.
     * @return An InputStreamReader for the specified file, allowing the caller to read the file
     * with the desired format applied.
     * @throws IOException If an I/O error occurs during the creation of the reader.
     */
    public static InputStreamReader createUniversalFileReader(final File file, final String expectedFormat) throws IOException {
        return createUniversalFileReader(file, expectedFormat, true);
    }

    /**
     * Creates an InputStreamReader for reading a file, considering the specified encoding format
     * and whether UTF-8 should be accepted. Handles potential BOM for UTF-8 encoded files.
     *
     * @param file           The file to be read.
     * @param expectedFormat The expected encoding format of the file.
     * @param acceptUTF8     Indicates whether UTF-8 encoding should be accepted if detected.
     * @return An InputStreamReader for the specified file and encoding.
     * @throws IOException If there is an error accessing the file or reading its content.
     */
    public static InputStreamReader createUniversalFileReader(final File file, final String expectedFormat, final boolean acceptUTF8) throws IOException {
        String encoding = acceptUTF8 && isUTF8(file, expectedFormat)
                ? "UTF-8"
                : expectedFormat;

        boolean skipBOM = encoding.equals("UTF-8") && hasUTF8BOM(file);

        InputStreamReader result = new InputStreamReader(new FileInputStream(file), encoding);
        if (skipBOM) {
            int BOM = result.read();
            if (BOM != 0xFEFF) log.error("Skipping BOM but value not 0xFEFF!");
        }
        return result;
    }

    /**
     * Retrieves the parent directory of the given file or directory path.
     * <p>
     * If the given path does not have a parent directory, it defaults to returning the
     * current directory represented by ".".
     *
     * @param filename The file or directory path for which the parent directory is to be retrieved.
     * @return The parent directory of the given path, or "." if no parent directory exists.
     */
    public static String getParentDirectory(final String filename) {
        File file = new File(filename);
        return file.getParent() != null ? file.getParent() : ".";
    }

    /**
     * Extracts the name of a file from the given file path.
     *
     * @param filename The full path or name of the file. Must not be null.
     * @return The name of the file without any directory path.
     */
    public static String getFilename(final String filename) {
        File file = new File(filename);
        return file.getName();
    }

    /**
     * Reads the content of a file and returns it as a string, joining all lines with the system line separator.
     *
     * @param filename The name of the file to be read.
     * @return A string containing the content of the file with all lines joined by the system line separator.
     * @throws IOException If an I/O error occurs while reading the file.
     */
    public static String readFileContent(final String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(createUniversalFileReader(filename))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    /**
     * Creates a universal file reader for the specified file name.
     * This method initializes and returns an InputStreamReader to read
     * the content of the given file.
     *
     * @param filename The name or path of the file to be read.
     * @return An InputStreamReader for reading the specified file.
     * @throws IOException If an I/O error occurs while creating the file reader.
     */
    public static InputStreamReader createUniversalFileReader(final String filename) throws IOException {
        return createUniversalFileReader(new File(filename));
    }

    /**
     * Creates a universal file reader for the specified file using the default encoding and configuration.
     *
     * @param file The file to be read. Must not be null.
     * @return An InputStreamReader configured to read the specified file.
     * @throws IOException If an I/O error occurs while creating the reader.
     */
    public static InputStreamReader createUniversalFileReader(final File file) throws IOException {
        return createUniversalFileReader(file, DEFAULT_CHECK_ENCODING, true);
    }

    /**
     * Writes the given content to the specified file path. If the file already exists, it will be overwritten.
     *
     * @param path    The path of the file to write to. Must not be null and must be writable.
     * @param content The content to be written to the file. Must not be null.
     * @throws IOException If an I/O error occurs during writing to the file.
     */
    public static void writeFile(final Path path, final String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
            writer.write(content);
        }
    }

    /**
     * Deletes the specified file or directory. If the target is a directory, all its contents,
     * including subdirectories and files, will be deleted recursively.
     * If the target file or directory does not exist, the method immediately returns {@code true}.
     *
     * @param targetFile the {@code Path} of the file or directory to be deleted
     * @return {@code true} if the target file or directory was successfully deleted,
     * or if it does not exist; {@code false} if an error occurred during deletion
     */
    public static boolean remove(Path targetFile) {
        if (!Files.exists(targetFile)) {
            return true;
        }

        if (Files.isDirectory(targetFile)) {
            return removeDirectory(targetFile);
        }

        return targetFile.toFile().delete();
    }

    /**
     * Deletes the specified directory and all its contents, including subdirectories and files.
     * The method performs a recursive deletion, starting with the deepest entries in the directory tree.
     * If the directory does not exist, the method immediately returns true.
     *
     * @param targetDir the {@code Path} of the directory to be deleted
     * @return {@code true} if the directory and all its contents were successfully deleted
     * or if the directory does not exist; {@code false} if an error occurred during deletion
     */
    public static boolean removeDirectory(Path targetDir) {
        if (!Files.exists(targetDir)) {
            return true;
        }

        if (!Files.isDirectory(targetDir)) {
            return false;
        }

        try {
            Files.walk(targetDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (IOException ignored) {
            return false;
        }

        return true;
    }

}
