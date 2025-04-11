package de.neitzel.core.inject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that an annotated class is a configuration class within a dependency
 * injection framework. Classes annotated with {@code @Config} are used as markers
 * for defining settings and application-specific configurations required by the
 * dependency injection mechanism.
 *
 * Typically, configuration classes provide metadata required for setting up the
 * framework, such as specifying the base package to scan for components.
 *
 * This annotation must be applied at the type level and is retained at runtime to
 * facilitate reflection-based processing. It is intended to serve as a declarative
 * representation of configuration options for the dependency injection container.
 *
 * Attributes:
 * - {@code basePackage}: Specifies the package name where the framework should scan
 *   for classes annotated with dependency injection annotations such as {@code @Component}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Config {
    /**
     * Specifies the base package for component scanning within the dependency
     * injection framework. Classes within the defined package and its sub-packages
     * can be scanned and identified as candidates for dependency injection.
     *
     * @return the base package name as a string; returns an empty string by default
     *         if no specific package is defined.
     */
    String basePackage() default "";
}
