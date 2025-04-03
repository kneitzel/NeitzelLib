package de.neitzel.core.io;

import java.io.File;
import java.io.IOException;

/**
 * The ISO8859EncodingFileReader class extends the ConvertedEncodingFileReader to provide
 * file reading functionality with specific encoding support for ISO-8859-15.
 * This class ensures that the file content is either read directly in the ISO-8859-15 encoding
 * or converted transparently to this encoding if needed.
 */
public class ISO8859EncodingFileReader extends ConvertedEncodingFileReader {

    /**
     * Represents the target encoding format used by the ISO8859EncodingFileReader class.
     * The TARGET_FORMAT is set to "ISO-8859-15", which specifies an encoding standard
     * that is a variant of ISO-8859-1, adding the Euro symbol and other additional characters.
     * This constant is used to indicate the desired character encoding for reading file content.
     */
    private static final String TARGET_FORMAT = "ISO-8859-15";

    /**
     * Constructs an instance of ISO8859EncodingFileReader to read the provided file
     * while ensuring the encoding is set to ISO-8859-15. If the file is not in the
     * target encoding, this method transparently converts the file content to match
     * the encoding before reading.
     *
     * @param file The file to be read. Must exist and be accessible.
     * @throws IOException If the file does not exist, is inaccessible, or an error
     *                     occurs during the encoding conversion process.
     */
    public ISO8859EncodingFileReader(File file) throws IOException {
        super(file, TARGET_FORMAT);
    }

    /**
     * Constructs an ISO8859EncodingFileReader for reading a file with encoding conversion
     * to the ISO-8859-15 format. This constructor accepts the file path as a string.
     *
     * @param filename the path to the file to be read
     * @throws IOException if an I/O error occurs while accessing or reading the specified file
     */
    public ISO8859EncodingFileReader(String filename) throws IOException {
        super(filename, TARGET_FORMAT);
    }
}
