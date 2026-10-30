package de.neitzel.fx.view.fixture;

import java.util.List;

/**
 * Ordinary model used by view-loading tests.
 */
public class Person {

    private String name;
    private Integer age;
    private List<String> tags;

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

    /**
     * Returns the tags. The value may be null.
     *
     * @return the tags
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * Sets the tags.
     *
     * @param tags the tags
     */
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
