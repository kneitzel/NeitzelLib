package de.neitzel.fx.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.beans.property.*;

import org.junit.jupiter.api.Test;

public class GeneratedViewModelTest {
    public static class Node {
        private String name = "sample";
        private Node child;

        public String getName() {
            return name;
        }

        public void setName(String n) {
            name = n;
        }

        public Node getChild() {
            return child;
        }

        public void setChild(Node n) {
            child = n;
        }
    }

    public static class Special extends Node {}

    public static class Generated extends ViewModel {
        public Generated(Node n) {
            super(n);
        }

        public StringProperty nameProperty() {
            return (StringProperty) storedProperty("name");
        }

        @SuppressWarnings("unchecked")
        public Property<Generated> childProperty() {
            return (Property<Generated>) (Property<?>) storedProperty("child");
        }

        public Property<?> missingProperty() {
            return storedProperty("missing");
        }
    }

    public static class Custom extends Generated {
        public Custom(Node n) {
            super(n);
        }
    }

    public static class Other extends Generated {
        public Other(Node n) {
            super(n);
        }
    }

    public static class Specific extends Generated {
        public Specific(Special n) {
            super(n);
        }
    }

    @ViewModelRegistration(
            model = Node.class,
            generated = Generated.class,
            defaultType = Custom.class)
    @ViewModelRegistration(
            model = Special.class,
            generated = Specific.class,
            defaultType = Specific.class)
    public static class Registry implements ViewModelRegistry {
        public Registry() {
            throw new AssertionError("Provider executed");
        }
    }

    @Test
    void storedPropertiesWriteBackAndShareCyclesAndMessages() {
        Node n = new Node();
        n.setChild(n);
        var vm = (Custom) ViewModels.of(n);
        assertSame(vm.nameProperty(), vm.property("name"));
        vm.nameProperty().set("changed");
        assertEquals("changed", n.getName());
        assertSame(vm, vm.childProperty().getValue());
        assertSame(vm, ViewModels.of(n, Generated.class));
        vm.addMessage(new Message(Message.Severity.INFO, "notice", "name"));
        assertEquals("notice", vm.property("latestMessage").getValue());
        assertThrows(IllegalArgumentException.class, vm::missingProperty);
        assertThrows(IllegalArgumentException.class, () -> ViewModels.of(n, Other.class));
    }

    @Test
    void explicitAndMostSpecificDefaultsAndNullChildren() {
        Node n = new Node();
        var custom = (Other) ViewModels.of(n, Other.class);
        assertNull(custom.childProperty().getValue());
        assertSame(custom, ViewModels.of(n));
        assertInstanceOf(Specific.class, ViewModels.of(new Special()));
        assertThrows(IllegalArgumentException.class, () -> ViewModels.of(new Node(), String.class));
    }

    @ViewModelRegistration(
            model = Node.class,
            generated = Generated.class,
            defaultType = Other.class)
    public static class Conflict implements ViewModelRegistry {}

    public static class Failing extends Generated {
        public Failing(Node n) {
            super(n);
            throw new IllegalStateException("failed constructor");
        }
    }

    @Test
    void conflictingServicesIdentifyBothOrigins() throws Exception {
        var folder = java.nio.file.Files.createTempDirectory("viewmodel-services-");
        var service = folder.resolve("META-INF/services/de.neitzel.fx.view.ViewModelRegistry");
        java.nio.file.Files.createDirectories(service.getParent());
        java.nio.file.Files.writeString(service, Conflict.class.getName());
        try (var loader =
                new java.net.URLClassLoader(
                        new java.net.URL[] {folder.toUri().toURL()}, getClass().getClassLoader())) {
            var error =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> ViewModelDefaults.select(Node.class, null, loader));
            assertTrue(error.getMessage().contains(Registry.class.getName()));
            assertTrue(error.getMessage().contains(Conflict.class.getName()));
        } finally {
            try (var paths = java.nio.file.Files.walk(folder)) {
                for (var p : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    java.nio.file.Files.delete(p);
            }
        }
    }

    @Test
    void failedConstructionDoesNotRetainPartialInstance() {
        Node n = new Node();
        assertThrows(IllegalStateException.class, () -> ViewModels.of(n, Failing.class));
        assertInstanceOf(Custom.class, ViewModels.of(n));
    }
}
