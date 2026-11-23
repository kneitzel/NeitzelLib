package de.neitzel.fx.view;

import lombok.Getter;
import lombok.Setter;

/** Connects an FXML button or menu item to a command supplied by its ViewModel. */
@Getter
@Setter
public final class CommandBinding {

    /** The target control's {@code fx:id}. */
    private String control;

    /** The simple bean property name of the command on the ViewModel. */
    private String path;
}
