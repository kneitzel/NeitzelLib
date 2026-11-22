package de.neitzel.fx.control;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.util.Locale;

/** Builds one window's menu bar, using the native application Quit item on macOS. */
public final class PlatformMenuBar {
    private PlatformMenuBar() {}

    /** Supply fresh menus and a localized quit action for each window. */
    public static MenuBar create(Menu file, MenuItem quit, Menu... otherMenus) {
        var bar = new MenuBar(file);
        bar.getMenus().addAll(otherMenus);
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("mac")) {
            bar.setUseSystemMenuBar(true);
        } else {
            if (!file.getItems().isEmpty()) file.getItems().add(new SeparatorMenuItem());
            file.getItems().add(quit);
        }
        return bar;
    }
}
