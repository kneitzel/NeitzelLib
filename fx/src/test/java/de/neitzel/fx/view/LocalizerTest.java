package de.neitzel.fx.view;

import de.neitzel.fx.i18n.Localizer;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LocalizerTest {
    @TempDir Path folder;

    @Test
    void localePrecedesCatalogAndMissingKeysSurviveAnFxmlLoad() throws Exception {
        var app = new Localizer.Catalog(getClass(), "LocalizerTestApp");
        var library = new Localizer.Catalog(getClass(), "LocalizerTestLib");
        var languages = new Localizer(Locale.forLanguageTag("de-CH"), Locale.ENGLISH, app, library);
        Files.writeString(folder.resolve("Part.fxml"), """
                <?import javafx.scene.layout.Pane?>
                <Pane xmlns:fx="http://javafx.com/fxml/1" accessibleText="%part"/>
                """);
        var view = folder.resolve("Main.fxml");
        Files.writeString(view, """
                <?import javafx.scene.layout.VBox?>
                <VBox xmlns:fx="http://javafx.com/fxml/1" accessibleText="%absent"><fx:include source="Part.fxml"/></VBox>
                """);
        Files.writeString(folder.resolve("Main_de.properties"), "close=Schließen\npart=Outer\n");
        Files.writeString(folder.resolve("Part_de.properties"), "part=Inner\n");
        Files.writeString(folder.resolve("Main.properties"), "fallback=Root\n");
        var url = view.toUri().toURL();
        var texts = languages.forView(url, getClass());
        assertEquals("Schliessen", texts.text("close"));
        assertEquals("Application", texts.text("shared"));
        assertEquals("Fallback 2", texts.text("fallback", 2));
        assertEquals("Library", texts.text("library"));
        var root = (VBox) new FXMLLoader(url, texts).load();
        assertEquals("⟦absent⟧", root.getAccessibleText());
        assertEquals("Outer", root.getChildren().getFirst().getAccessibleText());
        assertEquals("Inner", languages.forView(folder.resolve("Part.fxml").toUri().toURL(), getClass()).text("part"));
        var label = texts.observable("close");
        languages.setLocale(Locale.GERMAN);
        assertEquals("Schließen", label.getValue());
        assertEquals("⟦absent⟧", texts.text("absent"));
        assertFalse(texts.defines("absent"));
        assertEquals(Locale.getDefault(Locale.Category.DISPLAY), new Localizer(null, Locale.ENGLISH, app).getLocale());
    }
}
