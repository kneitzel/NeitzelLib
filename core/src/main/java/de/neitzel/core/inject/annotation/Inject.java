package de.neitzel.core.inject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a field is a candidate for dependency injection within
 * a dependency injection framework. Fields annotated with {@code @Inject}
 * are automatically populated with the required component instance during runtime,
 * typically by the dependency injection container.
 * <p>
 * This annotation must be applied at the field level and is retained at runtime
 * to enable reflection-based identification and assignment of dependencies.
 * <p>
 * The framework's dependency resolution mechanism identifies the appropriate
 * instance to inject based on the field's type or custom configuration,
 * ensuring loose coupling and easier testability.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
}
