package de.neitzel.fx.view;

/**
 * One Column of a {@link Table}.
 * <p>
 * The Column names the header the developer sees and the Attribute of the row element
 * that the header, the Filter, and the Sort share.
 */
public class Column {

    /**
     * The header text.
     */
    private String header;

    /**
     * The Attribute of the row element.
     */
    private String attribute;

    /**
     * Sets the header text.
     *
     * @param header the header text
     */
    public void setHeader(String header) {
        this.header = header;
    }

    /**
     * Returns the header text.
     *
     * @return the header text
     */
    public String getHeader() {
        return header;
    }

    /**
     * Sets the Attribute name.
     *
     * @param attribute the Attribute of the row element
     */
    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    /**
     * Returns the Attribute name.
     *
     * @return the Attribute of the row element
     */
    public String getAttribute() {
        return attribute;
    }
}
