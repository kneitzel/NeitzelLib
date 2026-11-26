package de.neitzel.fx.view;

import de.neitzel.fx.view.chosen.BadgeController;
import de.neitzel.fx.view.chosen.CaptionAddressForm;
import de.neitzel.fx.view.chosen.DeskController;
import de.neitzel.fx.view.chosen.DeskGreeting;
import de.neitzel.fx.view.chosen.HandedNameController;
import de.neitzel.fx.view.chosen.TitledController;
import de.neitzel.fx.view.command.CommandController;
import de.neitzel.fx.view.component.ActionRow;
import de.neitzel.fx.view.component.AddressForm;
import de.neitzel.fx.view.component.CityFirstRow;
import de.neitzel.fx.view.component.EnvelopeForm;
import de.neitzel.fx.view.component.StreetCaption;
import de.neitzel.fx.view.component.VisitRow;
import de.neitzel.fx.view.computed.WorkingAgeController;
import de.neitzel.fx.view.fixture.AgeUnit;
import de.neitzel.fx.view.fixture.CheckedStreetConverter;
import de.neitzel.fx.view.fixture.PrefixedAgeConverter;
import de.neitzel.fx.view.fixture.ShownAgeConverter;
import de.neitzel.fx.view.managed.ViewController;
import de.neitzel.fx.view.readable.ReadableSourcesController;
import de.neitzel.fx.view.selection.SelectionSummaryController;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Explicit registration for the FX tests. Each test shares Models only within its own context. */
final class TestViews implements ViewDependencies {
    private static final Set<Class<?>> COMPONENTS = Set.of(ActionRow.class, AddressForm.class,
            CaptionAddressForm.class, CityFirstRow.class, EnvelopeForm.class, VisitRow.class);

    private final ViewModelContext models = new ViewModelContext();
    private final ViewController.Registry registry = new ViewController.Registry();
    private final StreetCaption captions = new StreetCaption();
    private final DeskGreeting greeting = new DeskGreeting();

    private final Map<Class<?>, Object> services = Map.of(
            ViewController.Registry.class, registry,
            CheckedStreetConverter.class, new CheckedStreetConverter(),
            PrefixedAgeConverter.class, new PrefixedAgeConverter(new AgeUnit()),
            ShownAgeConverter.class, new ShownAgeConverter(),
            ReadableSourcesController.Caption.class, new ReadableSourcesController.Caption());

    private final Map<Class<?>, Supplier<?>> factories = Map.ofEntries(
            Map.entry(ActionRow.class, ActionRow::new),
            Map.entry(AddressForm.class, () -> new AddressForm(captions)),
            Map.entry(BadgeController.class, BadgeController::new),
            Map.entry(CaptionAddressForm.class, CaptionAddressForm::new),
            Map.entry(CityFirstRow.class, CityFirstRow::new),
            Map.entry(CommandController.class, CommandController::new),
            Map.entry(DeskController.class, () -> new DeskController(greeting)),
            Map.entry(EnvelopeForm.class, EnvelopeForm::new),
            Map.entry(HandedNameController.class, HandedNameController::new),
            Map.entry(ReadableSourcesController.class, ReadableSourcesController::new),
            Map.entry(SelectionSummaryController.class, SelectionSummaryController::new),
            Map.entry(TitledController.class, TitledController::new),
            Map.entry(ViewController.class, () -> new ViewController(registry)),
            Map.entry(VisitRow.class, VisitRow::new),
            Map.entry(WorkingAgeController.class, WorkingAgeController::new));

    static LoadedView load(URL fxml, Object model, TestViews dependencies) {
        return ViewLoader.loadWithDependencies(fxml, model, dependencies, dependencies.models);
    }

    @Override
    public boolean isComponent(Class<?> type) {
        return COMPONENTS.contains(type);
    }

    @Override
    public <T> Optional<T> findService(Class<T> type) {
        return Optional.ofNullable(services.get(type)).map(type::cast);
    }

    @Override
    public <T> ViewObject<T> createViewObject(Class<T> type) {
        Supplier<?> factory = factories.get(type);
        if (factory == null) throw new IllegalArgumentException("Unregistered test View type " + type.getName());
        T object = type.cast(factory.get());
        return new ViewObject<>(object, () -> {
            if (object instanceof ViewController controller) controller.close();
        });
    }
}
