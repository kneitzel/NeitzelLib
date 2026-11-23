package de.neitzel.fx.view.command;

import de.neitzel.fx.view.Command;
import de.neitzel.fx.view.ViewModel;
import de.neitzel.fx.view.ViewModelClass;
import jakarta.inject.Named;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;

@Named
@ViewModelClass(CommandController.Model.class)
public final class CommandController {

    public static final class Subject { }

    @Getter
    public static final class Model extends ViewModel {
        private final BooleanProperty enabled = new SimpleBooleanProperty();
        private int calls;
        private final Command clear = new Command(enabled, () -> calls++);

        public Model(Subject bean) {
            super(bean);
        }
    }
}
