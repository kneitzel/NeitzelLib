package de.neitzel.injectfx;

import javafx.util.Callback;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class InjectingControllerFactory implements Callback<Class<?>, Object> {
    private final Map<Class<?>, Object> parameterMap;

    public InjectingControllerFactory(Map<Class<?>, Object> parameterMap) {
        this.parameterMap = parameterMap;
    }

    @Override
    public Object call(Class<?> controllerClass) {
        try {
            Optional<Constructor<?>> bestConstructor = Stream.of(controllerClass.getConstructors())
                    .filter(constructor -> canBeInstantiated(constructor, parameterMap))
                    .findFirst();

            if (bestConstructor.isPresent()) {
                Constructor<?> constructor = bestConstructor.get();
                Object[] parameters = Stream.of(constructor.getParameterTypes())
                        .map(parameterMap::get)
                        .toArray();

                return constructor.newInstance(parameters);
            } else {
                throw new IllegalStateException("Kein passender Konstruktor gefunden für " + controllerClass.getName());
            }
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Erstellen der Controller-Instanz für " + controllerClass.getName(), e);
        }
    }

    private boolean canBeInstantiated(Constructor<?> constructor, Map<Class<?>, Object> parameterMap) {
        return Stream.of(constructor.getParameterTypes()).allMatch(parameterMap::containsKey);
    }
}
