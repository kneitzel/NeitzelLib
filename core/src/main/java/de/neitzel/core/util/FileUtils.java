package de.neitzel.core.util;

import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.stream.Collectors;

/**
 * Utility Methods regarding files.
 */
@Slf4j
public class FileUtils {

    /**
     * Date/Time format to add to request files.
     */
    public static final SimpleDateFormat DEBUG_FILE_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss_SSS");

    /**
     * Default encoding used to check the File
     */
    public static final String DEFAULT_CHECK_ENCODING = "ISO-8859-15";

    /**
     * Default Buffer size.
     */
    public static final int BUFFER_SIZE = 1024;

    /**
     * Checks if a File is UTF-8 encoded.
     * <p>
     *     This method checks a file for
     *     - first 3 bytes for UTF-8 BOM (0xEF 0xBB 0xBF)
     *     - Existence of 0xC2 / 0xC3 character.
     * </p>
     * @param file File to check.
     * @param checkEncoding Encoding to use for the check, e.g. ISO-8859-15.
     * @return true if an UTF-8 file is found, else false.
     * @throws IOException IOException is thrown if the file could not be read.
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
     * Checks if the File has a UTF-8 BOM.
     * <p>
     *     This method checks a file for
     *     - first 3 bytes for UTF-8 BOM (0xEF 0xBB 0xBF)
     *     - Existence of 0xC2 / 0xC3 character.
     * </p>
     * @param file File to check.
     * @return true if an UTF-8 BOM Header was found.
     * @throws IOException IOException is thrown if the file could not be read.
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
     * Checks if a File is UTF-8 encoded.
     * <p>
     *     This method checks a file for
     *     - first 3 bytes for UTF-8 BOM (0xEF 0xBB 0xBF)
     *     - Existence of 0xC2 / 0xC3 character.
     * </p>
     * @param file File to check.
     * @return true if an UTF-8 file is found, else false.
     * @throws IOException IOException is thrown if the file could not be read.
     */
    public static boolean isUTF8(final File file) throws IOException {
        return isUTF8(file, DEFAULT_CHECK_ENCODING);
    }

    /**
     * Converts the given Textfile inFile to the outFile using the given encodings.
     * @param inFile File to read as UTF-8.
     * @param sourceFormat Format of the source file.
     * @param outFile File to write with ISO-8859-15 format.
     * @param targetFormat Format of the target file.
     * @throws IOException Thrown when files couldn't be read / written.
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
     * Creates a new InputStreamReader with the given format or UTF-8 if an UTF-8 file was recognized.
     * @param filename Name of file to read.
     * @return An InputStreamReader
     */
    public static InputStreamReader createUniversalFileReader(final String filename) throws IOException {
        return createUniversalFileReader(new File(filename));
    }

    /**
     * Creates a new InputStreamReader with the given format or UTF-8 if an UTF-8 file was recognized.
     * @param filename Name of file to read.
     * @param expectedFormat Expected format e.g. ISO-8859-15
     * @return An InputStreamReader
     */
    public static InputStreamReader createUniversalFileReader(final String filename, final String expectedFormat) throws IOException {
        return createUniversalFileReader(new File(filename), expectedFormat);
    }

    /**
     * Creates a new InputStreamReader with the given format or UTF-8 if an UTF-8 file was recognized.
     * @param file File to read.
     * @return An InputStreamReader
     */
    public static InputStreamReader createUniversalFileReader(final File file) throws IOException {
        return createUniversalFileReader(file, DEFAULT_CHECK_ENCODING, true);
    }

    /**
     * Creates a new InputStreamReader with the given format or UTF-8 if an UTF-8 file was recognized.
     * @param file File to read.
     * @param expectedFormat Expected format e.g. ISO-8859-15
     * @return An InputStreamReader
     */
    public static InputStreamReader createUniversalFileReader(final File file, final String expectedFormat) throws IOException {
        return createUniversalFileReader(file, expectedFormat, true);
    }

    /**
     * Creates a new InputStreamReader with the given format or UTF-8 if an UTF-8 file was recognized.
     * @param file File to read.
     * @param expectedFormat Expected format e.g. ISO-8859-15
     * @return An InputStreamReader
     */
    public static InputStreamReader createUniversalFileReader(final File file, final String expectedFormat, final boolean acceptUTF8) throws IOException {
        String encoding = acceptUTF8 && isUTF8(file, expectedFormat)
                ? "UTF-8"
                : expectedFormat;

        boolean skipBOM = encoding.equals("UTF-8") && hasUTF8BOM(file);

        InputStreamReader result = new InputStreamReader(new FileInputStream(file), encoding);
        if (skipBOM) {
            int BOM = result.read();
            if (BOM != 0xFEFF) log.error ("Skipping BOM but value not 0xFEFF!");
        }
        return result;
    }

    /**
     * Gets the parent directory of a file name.
     * @param filename Name of file.
     * @return Parent folder.
     */
    public static String getParentDirectory(final String filename) {
        File file = new File(filename);
        return file.getParent() != null ? file.getParent() : ".";
    }

    /**
     * Gets the core name of the file without path.
     * @param filename Filename with path.
     * @return Filename without path.
     */
    public static String getFilename(final String filename) {
        File file = new File(filename);
        return file.getName();
    }

    /**
     * Reads the content of a file (With the correct encoding!).
     * @param filename Name of file to read.
     * @return Content of the file or null in case of an error.
     * @throws IOException Throes an IOException if the file cannot be read.
     */
    public static String readFileContent(final String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(createUniversalFileReader(filename))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    /**
     * Writes the content given to a file.
     * @param path Path of the file to write.
     * @param content Content to write.
     * @throws IOException Thrown if the file could not be written.
     */
    public static void writeFile(final Path path, final String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
            writer.write(content);
        }
    }
}
