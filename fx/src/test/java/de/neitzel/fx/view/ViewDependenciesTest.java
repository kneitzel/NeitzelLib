package de.neitzel.fx.view;

import de.neitzel.fx.i18n.Localizer;
import de.neitzel.fx.view.component.AddressForm;
import de.neitzel.fx.view.component.StreetCaption;
import de.neitzel.fx.view.component.VisitRow;
import de.neitzel.fx.view.fixture.Address;
import de.neitzel.fx.view.fixture.AgeUnit;
import de.neitzel.fx.view.fixture.Host;
import de.neitzel.fx.view.fixture.Person;
import de.neitzel.fx.view.fixture.PrefixedAgeConverter;
import de.neitzel.fx.view.fixture.Visit;
import de.neitzel.fx.view.managed.ViewController;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewDependenciesTest {

    @BeforeAll
    static void startFx() throws InterruptedException {
        FxThread.start();
    }

    @Test
    void createsControllersForEachViewAndIncludeAndReleasesEachOnce() throws Exception {
        FxThread.onFx(() -> {
            var source = new Dependencies();
            try (var models = new ViewModelContext()) {
                var first = load("adapter-view.fxml", new Person("Ada"), source, models);
                var second = load("adapter-view.fxml", new Person("Grace"), source, models);
                var parent = controller(first, "#parent");
                var included = controller(first, "#included");
                var other = controller(second, "#parent");
                assertEquals(4, source.releases.size());
                assertNotSame(parent, included);
                assertNotSame(parent, other);
                first.close();
                first.close();
                assertEquals(1, source.releases.get(parent));
                assertEquals(1, source.releases.get(included));
                assertEquals(0, source.releases.get(other));
                assertEquals(0, parent.getCloseCount());
                assertSame(source.registry, source.findService(ViewController.Registry.class).orElseThrow());
            }
            assertTrue(source.releases.values().stream().allMatch(count -> count == 1));
        });
    }

    @Test
    void createsExactComponentsAndReleasesChildrenAsTheirModelsAreRemoved() throws Exception {
        FxThread.onFx(() -> {
            var source = new Dependencies();
            var person = new Person("Ada");
            person.setAddresses(new ArrayList<>(List.of(new Address("Main"))));
            var host = new Host();
            host.setVisits(new ArrayList<>(List.of(new Visit("Main", "Darmstadt"))));
            try (var models = new ViewModelContext()) {
                load("repeat-addresses.fxml", person, source, models);
                load("show-visits.fxml", host, source, models);
                var address = source.releases.keySet().stream().filter(AddressForm.class::isInstance)
                        .map(AddressForm.class::cast).findFirst().orElseThrow();
                var row = source.releases.keySet().stream().filter(VisitRow.class::isInstance)
                        .findFirst().orElseThrow();
                assertEquals("Lane", address.getCaption());
                assertNotNull(address.receivedViewModel());
                assertEquals("kept", address.streetTextAtHandOff());
                person.getAddresses().clear();
                host.getVisits().clear();
                assertEquals(1, source.releases.get(address));
                assertEquals(1, source.releases.get(row));
                var standalone = ViewLoader.loadComponentWithDependencies(
                        AddressForm.class, new Address("New"), source, models);
                assertNotSame(address, standalone.getRoot());

                source.addressFactory = ignored -> new AddressForm(new StreetCaption()) {};
                var wrong = assertThrows(IllegalStateException.class, () ->
                        ViewLoader.loadComponentWithDependencies(AddressForm.class, new Address("Bad"), source, models));
                assertTrue(wrong.getMessage().contains("Expected exact Component type"));
                source.addressFactory = null;
                var missing = assertThrows(IllegalStateException.class, () ->
                        ViewLoader.loadComponentWithDependencies(AddressForm.class, new Address("Bad"), source, models));
                assertTrue(missing.getMessage().contains("No Component registered"));
            }
            assertTrue(source.releases.values().stream().allMatch(count -> count == 1));
        });
    }

    @Test
    void rollsBackEveryAcquiredObjectWhenAComponentLoadFails() throws Exception {
        FxThread.onFx(() -> {
            var source = new Dependencies();
            source.failOnRelease = true;
            try (var models = new ViewModelContext()) {
                var failure = assertThrows(IllegalStateException.class, () ->
                        load("adapter-invalid.fxml", new Person("Ada"), source, models));
                assertTrue(failure.getMessage().contains("adapter-invalid.fxml"));
                assertTrue(failure.getCause().getSuppressed().length > 0);
                assertEquals(2, source.releases.size());
                assertTrue(source.releases.values().stream().allMatch(count -> count == 1));
            }
        });
    }

    @Test
    void resolvesConvertersAndDistinguishesMissingServicesFromLookupFailure() throws Exception {
        FxThread.onFx(() -> {
            var source = new Dependencies();
            var person = new Person("Ada");
            person.setAge(24);
            try (var models = new ViewModelContext()) {
                var loaded = load("age-both-converted.fxml", person, source, models);
                var fields = ((VBox) loaded.getRoot()).getChildren();
                assertEquals(new AgeUnit().prefix() + 24, ((TextField) fields.getFirst()).getText());
                source.services.remove(PrefixedAgeConverter.class);
                var missing = assertThrows(IllegalStateException.class, () ->
                        load("age-both-converted.fxml", person, source, models));
                assertTrue(missing.getMessage().contains(PrefixedAgeConverter.class.getName()));
                source.services.put(Localizer.class, new Localizer(Locale.GERMAN, Locale.ENGLISH,
                        new Localizer.Catalog(getClass(), "LocalizerTestApp")));
                var localized = load("adapter-localized.fxml", person, source, models);
                assertEquals("Application", localized.getRoot().getAccessibleText());
                source.lookupFailure = new IllegalArgumentException("Ambiguous Localizer");
                var unavailable = assertThrows(IllegalStateException.class, () ->
                        load("adapter-view.fxml", person, source, models));
                assertTrue(unavailable.getMessage().contains(Localizer.class.getName()));
                assertSame(source.lookupFailure, unavailable.getCause().getCause());
            }
        });
    }

    private static ViewController controller(LoadedView view, String id) {
        return (ViewController) view.getRoot().lookup(id).getUserData();
    }

    private static LoadedView load(String resource, Object model, ViewDependencies source, ViewModelContext models) {
        URL fxml = ViewDependenciesTest.class.getResource(resource);
        return ViewLoader.loadWithDependencies(fxml, model, source, models);
    }

    private static final class Dependencies implements ViewDependencies {
        private final ViewController.Registry registry = new ViewController.Registry();
        private final Map<Class<?>, Object> services = new HashMap<>(Map.of(
                ViewController.Registry.class, registry,
                PrefixedAgeConverter.class, new PrefixedAgeConverter(new AgeUnit())));
        private final Map<Object, Integer> releases = new IdentityHashMap<>();
        private Function<StreetCaption, AddressForm> addressFactory = AddressForm::new;
        private RuntimeException lookupFailure;
        private boolean failOnRelease;

        @Override
        public boolean isComponent(Class<?> type) {
            return type == VisitRow.class || (type == AddressForm.class && addressFactory != null);
        }

        @Override
        public <T> Optional<T> findService(Class<T> type) {
            if (type == Localizer.class && lookupFailure != null) throw lookupFailure;
            return Optional.ofNullable(services.get(type)).map(type::cast);
        }

        @Override
        public <T> ViewObject<T> createViewObject(Class<T> type) {
            Object instance;
            if (type == ViewController.class) {
                instance = new ViewController(registry);
            } else if (type == AddressForm.class) {
                instance = addressFactory.apply(new StreetCaption());
            } else if (type == VisitRow.class) {
                instance = new VisitRow();
            } else {
                throw new AssertionError("Unexpected DI type " + type);
            }
            releases.put(instance, 0);
            return new ViewObject<>(type.cast(instance), () -> {
                releases.merge(instance, 1, Integer::sum);
                if (failOnRelease) throw new IllegalStateException("release failed");
            });
        }
    }
}
