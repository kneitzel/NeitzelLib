package de.neitzel.fx.example.preview;

import de.neitzel.fx.example.DeskApp;
import de.neitzel.fx.example.model.Address;
import de.neitzel.fx.example.view.DeskController;
import de.neitzel.fx.example.view.PersonViewModel;
import de.neitzel.fxdesigner.preview.PreviewProvider;
import de.neitzel.fxdesigner.preview.PreviewSession;
import de.neitzel.injection.ApplicationContext;

/** Independent samples for the nested, repeated and table-row Address components. */
public final class AddressPreviewProvider implements PreviewProvider<Address, ApplicationContext> {
    @Override
    public Class<Address> modelType() {
        return Address.class;
    }

    @Override
    public PreviewSession<Address, ApplicationContext> open(String id) {
        if (!java.util.Set.of("address", "visit", "row").contains(id))
            throw new IllegalArgumentException("Unknown address example: " + id);
        var person = DeskApp.sample();
        var context = new ApplicationContext(DeskApp.class);
        // An independently previewed row still needs the parent action's state.
        // No Desk.fxml is loaded and no popup listener is installed.
        if ("row".equals(id))
            context.getComponent(DeskController.class).setViewModel(new PersonViewModel(person));
        return new SampleSession<>(
                "address".equals(id) ? person.getHome() : person.getVisits().getFirst(), context);
    }
}
