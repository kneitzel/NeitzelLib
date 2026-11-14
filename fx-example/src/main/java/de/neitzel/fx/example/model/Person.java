package de.neitzel.fx.example.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * An ordinary person the desk edits.
 * <p>
 * Read-write attributes are what the View binds to. The class carries no JavaFX types.
 */
@Getter
@Setter
public class Person {

    /**
     * The person's name.
     */
    private String name = "";

    /**
     * The person's age in years, or {@code null} when it has not been set.
     */
    private Integer age;

    /**
     * The home address.
     */
    private Address home;

    /**
     * The visit addresses. After the View loads, this is the list the visit table and Repeat watch.
     */
    private List<Address> visits = new ArrayList<>();

    /**
     * The visit selected in the table and the combo box, or {@code null} when none is selected.
     */
    private Address selectedVisit;

    /**
     * The tags. After the View loads, this is the list the list view watches.
     */
    private List<String> tags = new ArrayList<>();

    /**
     * Whether the person is active. A primitive boolean cannot be null.
     */
    private boolean active;

    /**
     * The person's role, or {@code null} when none is set.
     */
    private Role role;

    /**
     * Creates an empty person.
     */
    public Person() {
    }

}
