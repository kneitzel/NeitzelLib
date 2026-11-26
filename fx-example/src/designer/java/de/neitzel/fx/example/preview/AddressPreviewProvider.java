package de.neitzel.fx.example.preview;

import de.neitzel.fx.example.DeskApp;
import de.neitzel.fx.example.model.Address;
import de.neitzel.fxdesigner.preview.PreviewProvider;
import de.neitzel.fxdesigner.preview.PreviewSession;
import de.neitzel.fx.view.ViewDependencies;

/** Independent samples for the nested, repeated and table-row Address components. */
public final class AddressPreviewProvider implements PreviewProvider<Address, ViewDependencies> {
    @Override
    public Class<Address> modelType() {
        return Address.class;
    }

    @Override
    public PreviewSession<Address, ViewDependencies> open(String id) {
        if (!java.util.Set.of("address", "visit", "row").contains(id))
            throw new IllegalArgumentException("Unknown address example: " + id);
        var person = DeskApp.sample();
        var context = DeskApp.dependencies(person);
        return new SampleSession<>(
                "address".equals(id) ? person.getHome() : person.getVisits().getFirst(), context);
    }
}
