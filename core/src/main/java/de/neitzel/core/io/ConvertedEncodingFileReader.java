package de.neitzel.core.io;

import de.neitzel.core.util.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;

/**
 * FileReader that converts an UTF-8 file to ISO-8859-15 if required.
 * <p>
 *     This FileReader checks, if the file to be read is an UTF-8 file.
 *     If an UTF-8 encoding is found, a temporary file will be created with the content
 *     of the original File - just encoded in the new format.
 * </p>
 * <p>
 *     This Reader is mainly tested ith ISO-8859-15 and UTF-8. Other formats are not really supported.
 * </p>
 */
@Slf4j
public class ConvertedEncodingFileReader extends InputStreamReader {

    private static String checkEncoding = "ISO-8859-15";

    private static void setCheckEncoding(final String encoding) {
        if (Charset.forName(encoding) != null) throw new IllegalCharsetNameException("Encoding " + encoding + " is not supported!");

        checkEncoding = encoding;
    }

    /**
     * Creates a new ConvertedEncodingFileReader from a given File.
     * @param file File to convert if required and open reader.
     * @param targetFormat Target Format to use.
     * @throws IOException
     */
    public ConvertedEncodingFileReader(final File file, final String targetFormat) throws IOException {
        super(createTargetFormatInputFileStream(file, targetFormat), targetFormat);
    }

    /**
     * Creates a new ISO8859ConvertedFileReader from a given File.
     * @param filename File to convert if required and open reader.
     * @throws IOException
     */
    public ConvertedEncodingFileReader(final String filename, final String targetFormat) throws IOException {
        this(new File(filename), targetFormat);
    }

    /**
     * Creates an ISO8859-15 encoded InputFileStream on an file.
     * <p>
     *     If the file is UTF-8 encoded, then it is converted to a temp file and the temp file will be opened.
     * </p>
     * @param file
     * @return The InputFileStream on the original file or the converted file in case of an UTF-8 file.
     * @throws IOException
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
}
