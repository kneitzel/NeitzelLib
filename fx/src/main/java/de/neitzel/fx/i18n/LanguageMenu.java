package de.neitzel.fx.i18n;

import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Window;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Language selection shared by desktop windows. Only a changed user selection shows a notice. */
public final class LanguageMenu {
    public static final Localizer.Catalog CATALOG = new Localizer.Catalog(LanguageMenu.class, "NeitzelLib-fx", "de.neitzel.fx");

    private LanguageMenu() {}

    public static Menu create(Localizer languages, Supplier<Window> owner, Consumer<Locale> remember,
                              Locale... choices) {
        var texts = languages.forClass(LanguageMenu.class);
        var menu = new Menu();
        menu.textProperty().bind(languages.localeProperty().map(locale -> texts.text("language.menu")));
        var group = new ToggleGroup();
        for (Locale choice : choices) {
            var item = new RadioMenuItem(choice.getDisplayName(choice));
            item.setToggleGroup(group);
            item.setUserData(choice);
            item.setOnAction(event -> {
                if (choice.equals(languages.getLocale())) return;
                languages.setLocale(choice);
                remember.accept(choice);
                var notice = new Alert(Alert.AlertType.INFORMATION, texts.text("language.reopen"));
                notice.initOwner(owner.get());
                notice.setTitle(texts.text("language.menu"));
                notice.setHeaderText(null);
                notice.show();
            });
            menu.getItems().add(item);
        }
        menu.setOnShowing(event -> {
            for (var toggle : group.getToggles()) {
                Locale choice = (Locale) toggle.getUserData();
                toggle.setSelected(choice.getLanguage().equals(languages.getLocale().getLanguage()));
            }
        });
        return menu;
    }
}
