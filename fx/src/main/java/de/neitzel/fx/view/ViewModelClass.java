package de.neitzel.fx.view;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names the ViewModel class for a Controller or a Component.
 * <p>
 * The library constructs that class with the Model instance for the first View of the instance.
 * A later View that names a different class fails to load. A later View that names none reuses
 * the ViewModel already built. The container does not create the ViewModel.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ViewModelClass {

    /**
     * Returns the ViewModel class.
     * <p>
     * The class has a constructor that takes the Model instance. It may extend {@link ViewModel}
     * or be another object that exposes JavaFX properties by name.
     *
     * @return the ViewModel class
     */
    Class<?> value();
}
