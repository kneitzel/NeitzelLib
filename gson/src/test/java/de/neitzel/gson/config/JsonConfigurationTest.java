package de.neitzel.gson.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Checks that {@link JsonConfiguration#get} reads stored JSON, missing keys, defaults, and objects.
 */
class JsonConfigurationTest {

    /**
     * Test class for JsonConfiguration containing unit tests for the `get` method.
     * The `get` method fetches the value associated with a given key and converts
     * it from JSON representation to the specified class type.
     */

    /**
     * Returns the integer stored for an existing key.
     */
    @Test
    void testGetExistingKeyWithValidValue() {
        // Arrange
        Gson gson = new Gson();
        HashMap<String, String> settings = new HashMap<>();
        settings.put("testKey", gson.toJson(42));
        JsonConfiguration jsonConfiguration = JsonConfiguration.builder()
                .settings(settings)
                .gson(gson)
                .build();

        // Act
        Integer value = jsonConfiguration.get("testKey", Integer.class);

        // Assert
        assertEquals(42, value);
    }

    /**
     * Returns null when the requested key is absent.
     */
    @Test
    void testGetNonExistingKey() {
        // Arrange
        Gson gson = new Gson();
        HashMap<String, String> settings = new HashMap<>();
        JsonConfiguration jsonConfiguration = JsonConfiguration.builder()
                .settings(settings)
                .gson(gson)
                .build();

        // Act
        String value = jsonConfiguration.get("nonExistentKey", String.class);

        // Assert
        assertNull(value);
    }

    /**
     * Returns the stored value when the key exists, even if a default was supplied.
     */
    @Test
    void testGetWithDefaultValueWhenKeyExists() {
        // Arrange
        Gson gson = new Gson();
        HashMap<String, String> settings = new HashMap<>();
        settings.put("testKey", gson.toJson("Hello World"));
        JsonConfiguration jsonConfiguration = JsonConfiguration.builder()
                .settings(settings)
                .gson(gson)
                .build();

        // Act
        String value = jsonConfiguration.get("testKey", String.class, "Default Value");

        // Assert
        assertEquals("Hello World", value);
    }

    /**
     * Returns the supplied default when the key is absent.
     */
    @Test
    void testGetWithDefaultValueWhenKeyDoesNotExist() {
        // Arrange
        Gson gson = new Gson();
        HashMap<String, String> settings = new HashMap<>();
        JsonConfiguration jsonConfiguration = JsonConfiguration.builder()
                .settings(settings)
                .gson(gson)
                .build();

        // Act
        String value = jsonConfiguration.get("nonExistentKey", String.class, "Default Value");

        // Assert
        assertEquals("Default Value", value);
    }

    /**
     * Reads a JSON object into {@link TestObject}.
     */
    @Test
    void testGetWithComplexObject() {
        // Arrange
        Gson gson = new Gson();
        HashMap<String, String> settings = new HashMap<>();
        TestObject testObject = new TestObject("John Doe", 30);
        settings.put("complexKey", gson.toJson(testObject));
        JsonConfiguration jsonConfiguration = JsonConfiguration.builder()
                .settings(settings)
                .gson(gson)
                .build();

        // Act
        TestObject result = jsonConfiguration.get("complexKey", TestObject.class);

        // Assert
        assertEquals("John Doe", result.name());
        assertEquals(30, result.age());
    }

    // Helper class for complex object tests
    /**
     * Value deserialized in the complex-object test.
     */
    static class TestObject {
        /**
         * The sample name.
         */
        private final String name;

        /**
         * The sample age.
         */
        private final int age;

        /**
         * Creates a sample with the given name and age.
         *
         * @param name the sample name
         * @param age the sample age
         */
        TestObject(String name, int age) {
            this.name = name;
            this.age = age;
        }

        /**
         * Returns the sample name.
         *
         * @return the name
         */
        public String name() {
            return name;
        }

        /**
         * Returns the sample age.
         *
         * @return the age
         */
        public int age() {
            return age;
        }
    }
}