package de.neitzel.core.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationDirectoriesTest {
    @Test void macPreferencesUseApplicationSupport() {
        assertEquals(Path.of("/Users/test/Library/Application Support/ActiveSideDeviceFX"),
                ApplicationDirectories.configurationDirectory("ActiveSideDeviceFX", "Mac OS X", Path.of("/Users/test"), Map.of()));
    }
    @Test void platformOverridesUseNativeFoldersWithoutRelativePaths() {
        assertEquals(Path.of("/roaming/ActiveSideDeviceFX"), ApplicationDirectories.configurationDirectory(
                "ActiveSideDeviceFX", "Windows 11", Path.of("/users/test"), Map.of("APPDATA", "/roaming")));
        assertEquals(Path.of("/users/test/AppData/Roaming/ActiveSideDeviceFX"), ApplicationDirectories.configurationDirectory(
                "ActiveSideDeviceFX", "Windows 11", Path.of("/users/test"), Map.of()));
        assertEquals(Path.of("/config/ActiveSideDeviceFX"), ApplicationDirectories.configurationDirectory(
                "ActiveSideDeviceFX", "Linux", Path.of("/home/test"), Map.of("XDG_CONFIG_HOME", "/config")));
        assertEquals(Path.of("/home/test/.config/ActiveSideDeviceFX"), ApplicationDirectories.configurationDirectory(
                "ActiveSideDeviceFX", "Linux", Path.of("/home/test"), Map.of("XDG_CONFIG_HOME", "relative")));
    }
    @Test void applicationNameCannotEscapeItsNativeDirectory() {
        for (String invalid : new String[]{"", ".", "..", "../elsewhere", "/absolute", "nested/name", "nested\\name"}) {
            assertThrows(IllegalArgumentException.class, () -> ApplicationDirectories.configurationDirectory(
                    invalid, "Linux", Path.of("/home/test"), Map.of()), invalid);
        }
    }
}
