package de.neitzel.fx.view.fixture;

/**
 * Ordinary model used by view-loading tests.
 */
public class Person {

    private String name;
    private Integer age;

    /**
     * Creates a person with the given name.
     *
     * @param name the current name
     */
    public Person(String name) {
        this.name = name;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns a title that cannot be written.
     *
     * @return the title
     */
    public String getTitle() {
        return "Doctor";
    }

    /**
     * Returns the age. The value may be null.
     *
     * @return the age
     */
    public Integer getAge() {
        return age;
    }

    /**
     * Sets the age.
     *
     * @param age the age
     */
    public void setAge(Integer age) {
        this.age = age;
    }
}
