package de.neitzel.fx.example.view;

import de.neitzel.fx.example.model.Address;
import de.neitzel.fx.view.HasViewModel;
import de.neitzel.fx.view.Message;
import de.neitzel.fx.view.ViewModel;
import de.neitzel.fx.view.ViewModelClass;
import jakarta.inject.Singleton;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller for the person desk.
 * <p>
 * The container creates this class. {@link #setViewModel} receives the view model before the
 * binding elements run, so {@link #greeting()} can read the name field and the age on its first
 * call. {@link PersonViewModel} is the view model class for this controller.
 */
@Singleton
@ViewModelClass(PersonViewModel.class)
public class DeskController implements HasViewModel {

    /**
     * The name field. {@link #greeting()} reads it.
     */
    @FXML
    private TextField nameField;

    /**
     * The tag field cleared after {@link #addTag()}.
     */
    @FXML
    private TextField tagField;

    /**
     * The person view model, set before the bindings run.
     */
    private ViewModel viewModel;

    /**
     * Creates the controller. The container calls this constructor.
     */
    public DeskController() {
    }

    /**
     * Receives the view model for the person on the desk.
     *
     * @param viewModel the view model
     */
    @Override
    public void setViewModel(ViewModel viewModel) {
        this.viewModel = viewModel;
    }

    /**
     * Returns the greeting written to the label.
     * <p>
     * The method reads the name field and the age on the view model. It does not write the model.
     * A change of either one calls it again.
     *
     * @return the greeting
     */
    public String greeting() {
        String name = nameField.getText();
        Object age = viewModel.property("age").getValue();
        if (name == null || name.isBlank()) {
            return "Hello (" + age + ")";
        }
        return "Hello, " + name.trim() + " (" + age + ")";
    }

    /**
     * Adds a visit and leaves the previous selection in place.
     */
    @FXML
    public void addVisit() {
        Address created = new Address("New street", "New city");
        created.setVisitedOn(LocalDate.now());
        visits().add(created);
    }

    /**
     * Removes a visit from the list the table and Repeat are watching.
     *
     * @param visit the visit
     */
    public void removeVisit(Address visit) {
        if (visit == null) {
            return;
        }
        visits().remove(visit);
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
     * Adds an info message that quotes the name already stored on the model.
     */
    @FXML
    public void leaveNote() {
        leave(Message.Severity.INFO, "The model already has the name " + name() + ".");
    }

    /**
     * Adds a warning. The notice label shows it, and it is not written to the log.
     */
    @FXML
    public void leaveWarning() {
        leave(Message.Severity.WARN, "Check the age before leaving the desk.");
    }

    /**
     * Adds a fatal notice. The notice label shows it, and it is written to the log.
     */
    @FXML
    public void leaveFatal() {
        leave(Message.Severity.FATAL, "The desk cannot store this person.");
    }

    /**
     * Adds a notice whose source is this desk.
     *
     * @param severity how serious the notice is
     * @param text     the notice text
     */
    private void leave(Message.Severity severity, String text) {
        viewModel.addMessage(new Message(severity, text, "desk"));
    }

    /**
     * Returns the name currently held by the view model.
     *
     * @return the name
     */
    private Object name() {
        return viewModel.property("name").getValue();
    }

    /**
     * Returns the visit list the view is watching.
     *
     * @return the visits
     */
    @SuppressWarnings("unchecked")
    private List<Address> visits() {
        return (List<Address>) viewModel.property("visits").getValue();
    }

    /**
     * Returns the tag list the view is watching.
     *
     * @return the tags
     */
    @SuppressWarnings("unchecked")
    private List<String> tags() {
        return (List<String>) viewModel.property("tags").getValue();
    }
}
