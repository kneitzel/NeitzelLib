package de.neitzel.fx.view;

import java.lang.annotation.*;

/** A compiled, non-executing default declaration contributed by a registry service. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(ViewModelRegistrations.class)
public @interface ViewModelRegistration {
    Class<?> model();

    Class<? extends ViewModel> generated();

    Class<? extends ViewModel> defaultType();
}
