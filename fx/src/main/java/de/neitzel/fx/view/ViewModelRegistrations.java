package de.neitzel.fx.view;

import java.lang.annotation.*;

/** Container for generated registry declarations. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ViewModelRegistrations {
    ViewModelRegistration[] value();
}
