package de.neitzel.core.io;

import java.io.File;
import java.io.IOException;

public class ISO8859EncodingFileReader extends ConvertedEncodingFileReader {

    private static final String TARGET_FORMAT = "ISO-8859-15";

    public ISO8859EncodingFileReader(File file) throws IOException {
        super(file, TARGET_FORMAT);
    }

    public ISO8859EncodingFileReader(String filename) throws IOException {
        super(filename, TARGET_FORMAT);
    }
}
