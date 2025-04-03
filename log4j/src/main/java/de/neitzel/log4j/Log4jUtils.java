package de.neitzel.log4j;

import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.net.URISyntaxException;

/**
 * Adds Utility Methods for log4j.
 */
public class Log4jUtils {

    /**
     * Default Logfile that should be read.
     * Must be a relative path that is checked from the current directory and also from the location of the jar file.
     */
    public static final String DEFAULT_LOG4J_LOGFILE = "./log4j.properties";

    /**
     * Resource to use when no file is found.
     */
    public static final String DEFAULT_LOG4J_RESOURCE = "/log4j.default.properties";

    /**
     * Checks if a log4j config file was set on command line with -Dlog4j.configuration
     * @return true if log4j.configuration was set.
     */
    public static boolean isLog4jConfigFileSet() {
        return System.getProperty("log4j.configuration") != null;
    }

    /**
     * Uses the default configurations if no config file was set.
     */
    public static void setLog4jConfiguration() {
        setLog4jConfiguration(DEFAULT_LOG4J_LOGFILE, DEFAULT_LOG4J_RESOURCE);
    }

    /**
     * Gets the file with path relative to the jar file.
     * @param log4jConfigFile log4j config file.
     * @return Path if possible or null.
     */
    public static String getLog4jLogfileAtJar(final String log4jConfigFile) {
        try {
            return new File(Log4jUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent() + "/" + log4jConfigFile;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    /**
     * Uses the default configurations if no config file was set.
     * <p>
     *     A log4j configuration can be set using -Dlog4j.configuration. If no configuration was set,
     *     we look for the log4jConfigFile. If it does not exist, we read the defaultResource.
     * </p>
     * @param log4jConfigFile Default file to look for.
     * @param defaultResource Resource path to use if config file wasn't found.
     */
    public static void setLog4jConfiguration(final String log4jConfigFile, final String defaultResource) {
        if (isLog4jConfigFileSet()) return;

        String fileAtJar = getLog4jLogfileAtJar(log4jConfigFile);

        if (new File(log4jConfigFile).exists()) {
            PropertyConfigurator.configure(log4jConfigFile);
        } else if (fileAtJar != null && new File(fileAtJar).exists()) {
            System.out.println("Nutze Log4J Konfiguration bei jar File: " + fileAtJar);
            PropertyConfigurator.configure(fileAtJar);
        }else {
            PropertyConfigurator.configure(Log4jUtils.class.getResource(defaultResource));
        }
    }
}
