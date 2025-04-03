package de.neitzel.log4j;

import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.net.URISyntaxException;

/**
 * Utility class for managing Log4j configurations. Provides methods to set up
 * Log4j configurations from default files, resources, or command line arguments.
 */
public class Log4jUtils {

    /**
     * The default path to the Log4j configuration file.
     * This variable is used to specify the local file path for the Log4j configuration
     * when no custom configuration is provided.
     */
    public static final String DEFAULT_LOG4J_LOGFILE = "./log4j.properties";

    /**
     * The default resource path to the Log4j configuration file included in the classpath.
     * This path is used as a fallback when no other Log4j configuration is explicitly set.
     */
    public static final String DEFAULT_LOG4J_RESOURCE = "/log4j.default.properties";

    /**
     * Checks if the system property "log4j.configuration" is set.
     *
     * @return true if the "log4j.configuration" property is defined, false otherwise.
     */
    public static boolean isLog4jConfigFileSet() {
        return System.getProperty("log4j.configuration") != null;
    }

    /**
     * Configures Log4j using default configuration settings.
     * This method leverages a default configuration file path and a default resource path
     * to set up Log4j logging if a configuration file is not already specified via
     * a system property. If a valid configuration file or resource is found, it will be applied.
     *
     * Delegates to the overloaded {@code setLog4jConfiguration(String log4jConfigFile, String defaultResource)}
     * method using predefined defaults.
     */
    public static void setLog4jConfiguration() {
        setLog4jConfiguration(DEFAULT_LOG4J_LOGFILE, DEFAULT_LOG4J_RESOURCE);
    }

    /**
     * Constructs the absolute path to the specified Log4j configuration file located
     * in the same directory as the JAR file of the application.
     *
     * @param log4jConfigFile The name of the Log4j configuration file.
     * @return The absolute path to the specified Log4j configuration file if the
     *         path is successfully constructed; otherwise, returns null in case of an error.
     */
    public static String getLog4jLogfileAtJar(final String log4jConfigFile) {
        try {
            return new File(Log4jUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent() + "/" + log4jConfigFile;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    /**
     * Sets the Log4j configuration using the specified configuration file or a default resource
     * if the configuration file is not found. If a Log4j configuration is already set, the method
     * does nothing.
     *
     * @param log4jConfigFile the path to the Log4j configuration file to be used.
     * @param defaultResource the fallback resource to be used as the configuration if the file
     *                        is not found or accessible.
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
