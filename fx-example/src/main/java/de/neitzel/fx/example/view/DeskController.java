package de.neitzel.fx.example.view;

import de.neitzel.fx.example.model.Address;
import de.neitzel.fx.view.HasViewModel;
import de.neitzel.fx.view.Message;
import de.neitzel.fx.view.ViewModel;
import jakarta.inject.Singleton;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

import java.util.List;

/**
 * Controller for the person desk.
 * <p>
 * The container creates this class and injects nothing else. {@link #setViewModel} receives the
 * ViewModel before the binding elements run, so {@link #greeting()} can read the name field on
 * its first call.
 */
@Singleton
public class DeskController implements HasViewModel {

    @FXML
    private TextField nameField;

    @FXML
    private TextField tagField;

    private ViewModel viewModel;

    /**
     * Creates the controller. The container calls this constructor.
     */
    public DeskController() {
    }

    /**
     * Receives the ViewModel for the person on the desk.
     *
     * @param viewModel the ViewModel
     */
    @Override
    public void setViewModel(ViewModel viewModel) {
        this.viewModel = viewModel;
    }

    /**
     * Returns the greeting written to the label.
     * <p>
     * The method reads the name field. It does not write the Model.
     *
     * @return the greeting
     */
    public String greeting() {
        String name = nameField.getText();
        if (name == null || name.isBlank()) {
            return "Hello";
        }
        return "Hello, " + name.trim();
    }

    /**
     * Adds a visit and leaves the previous selection in place.
     */
    @FXML
    public void addVisit() {
        visits().add(new Address("New street", "New city"));
    }

    /**
     * Adds the tag field's text to the tag list and clears the field.
     */
    @FXML
    public void addTag() {
        String tag = tagField.getText();
        if (tag == null || tag.isBlank()) {
            return;
        }
        tags().add(tag.trim());
        tagField.clear();
    }

    /**
     * Adds an info Message that quotes the name already stored on the Model.
     */
    @FXML
    public void leaveNote() {
        Object name = viewModel.property("name").getValue();
        viewModel.addMessage(new Message(
                Message.Severity.INFO,
                "The model already has the name " + name + ".",
                "desk"));
    }

    @SuppressWarnings("unchecked")
    private List<Address> visits() {
        return (List<Address>) viewModel.property("visits").getValue();
    }

    @SuppressWarnings("unchecked")
    private List<String> tags() {
        return (List<String>) viewModel.property("tags").getValue();
    }
}
