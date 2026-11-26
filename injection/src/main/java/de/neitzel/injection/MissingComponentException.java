package de.neitzel.injection;

import lombok.Getter;

/** A lookup requested a type with no registration in the ApplicationContext. */
@Getter
public final class MissingComponentException extends IllegalArgumentException {

    private final Class<?> componentType;

    public MissingComponentException(Class<?> componentType) {
        super("No component found for type: " + componentType.getName());
        this.componentType = componentType;
    }
}
