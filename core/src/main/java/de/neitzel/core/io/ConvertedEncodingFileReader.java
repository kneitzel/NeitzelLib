package de.neitzel.core.io;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;

/**
 * The ConvertedEncodingFileReader class extends {@link InputStreamReader} to provide functionality
 * for handling file encoding conversion transparently. If a file is detected to be in UTF-8 encoding,
 * this class converts it to the specified target encoding using a temporary file, then opens the reader
 * with the converted encoding. If the file is already in the target encoding, it opens the reader directly.
 * <p>
 * This class is useful for applications needing to process text files in specific encodings and ensures
 * encoding compatibility.
 */
@Slf4j
public class ConvertedEncodingFileReader extends InputStreamReader {

    /**
     * The `checkEncoding` variable specifies the default encoding to be used
     * when verifying file encodings within the `ConvertedEncodingFileReader` class.
     * This encoding is primarily used to determine whether a file needs conversion
     * to the target format or can be read directly in its existing format.
     * The default value is set to "ISO-8859-15".
     * <p>
     * Modifying this variable requires careful consideration, as it affects
     * the behavior of methods that rely on encoding validation, particularly
     * in the process of detecting UTF-8 files or converting them during file reading.
     */
    private static String checkEncoding = "ISO-8859-15";

    /**
     * Constructs a ConvertedEncodingFileReader for reading a file with encoding conversion support.
     * This constructor takes the file path as a string and ensures the file's encoding is either
     * converted to the specified target format or read directly if it matches the target format.
     *
     * @param filename     the path to the file to be read
     * @param targetFormat the target encoding format to use for reading the file
     * @throws IOException if an I/O error occurs while accessing or reading the specified file
     */
    public ConvertedEncodingFileReader(final String filename, final String targetFormat) throws IOException {
        this(new File(filename), targetFormat);
    }

    /**
     * Constructs a ConvertedEncodingFileReader for a specified file and target encoding format.
     * The class reads the provided file and ensures that its content is handled in the target encoding.
     * If the file is not already in the target encoding, it converts the file's encoding
     * transparently using a temporary file before reading it.
     *
     * @param file         The file to be read. Must exist and be accessible.
     * @param targetFormat The target character encoding format to which the file content should be converted.
     * @throws IOException If the file does not exist, is inaccessible, or an error occurs during the encoding conversion process.
     */
    public ConvertedEncodingFileReader(final File file, final String targetFormat) throws IOException {
        super(createTargetFormatInputFileStream(file, targetFormat), targetFormat);
    }

    /**
     * Creates an input file stream for a given file, converting its encoding if necessary.
     * If the file is not in UTF-8 encoding, a direct {@link FileInputStream} is returned for the file.
     * If the file is in UTF-8 encoding, it is converted to the specified target format using a temporary file,
     * and then an input stream for the temporary file is returned.
     *
     * @param file         the file for which the input stream is to be created
     * @param targetFormat the desired target encoding format
     * @return a {@link FileInputStream} for the file or a temporary file with converted encoding
     * @throws IOException if the file does not exist or an error occurs during file operations
     */
    private static FileInputStream createTargetFormatInputFileStream(final File file, final String targetFormat) throws IOException {
        if (!file.exists()) {
            String errorMessage = "File " + file.toString() + " does not exist!";
            log.error(errorMessage);
            throw new FileNotFoundException(errorMessage);
        }

        if (!FileUtils.isUTF8(file, checkEncoding)) {
            return new FileInputStream(file);
        } else {
            File tempFile = File.createTempFile(file.getName(), "tmp");
            FileUtils.convertTextFile(file, "UTF-8", tempFile, targetFormat);
            tempFile.deleteOnExit();
            return new FileInputStream(tempFile);
        }
    }

    /**
     * Sets the encoding that will be used to check the file encoding for compatibility.
     * Throws an exception if the specified encoding is not valid or supported.
     *
     * @param encoding the name of the character encoding to set as the check encoding;
     *                 it must be a valid and supported Charset.
     * @throws IllegalCharsetNameException if the specified encoding is not valid or supported.
     */
    private static void setCheckEncoding(final String encoding) {
        if (Charset.forName(encoding) != null)
            throw new IllegalCharsetNameException("Encoding " + encoding + " is not supported!");

        checkEncoding = encoding;
    }
}
