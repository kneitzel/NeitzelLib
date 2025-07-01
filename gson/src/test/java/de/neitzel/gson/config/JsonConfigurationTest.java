package de.neitzel.gson.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonConfigurationTest {

    /**
     * Test class for JsonConfiguration containing unit tests for the `get` method.
     * The `get` method fetches the value associated with a given key and converts
     * it from JSON representation to the specified class type.
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
    static class TestObject {
        private final String name;

        private final int age;

        TestObject(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String name() {
            return name;
        }

        public int age() {
            return age;
        }
    }
}