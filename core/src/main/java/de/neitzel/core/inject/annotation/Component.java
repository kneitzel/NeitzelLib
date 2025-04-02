package de.neitzel.core.inject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that an annotated class is a "component" within a dependency injection framework.
 * Classes annotated with {@code @Component} are recognized during the component scanning process
 * as candidates for instantiation and management by the dependency injection framework.
 *
 * This annotation is typically used on classes that represent application-specific components,
 * such as service implementations, controllers, or other objects intended to be instantiated
 * and injected into other components during runtime.
 *
 * The annotation must be placed at the type level, and it is retained at runtime
 * to allow for reflection-based scanning of classes within specified packages.
 *
 * Usage of this annotation assumes that there exists a component scanning mechanism
 * that processes annotated classes and identifies their roles, dependencies, and hierarchy
 * within the application's structure.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {
}
