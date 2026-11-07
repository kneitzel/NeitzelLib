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
     * The starting width, or {@code null} when this Column takes an equal share of the Table.
     */
    private Double width;

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

    /**
     * Sets the starting width.
     * <p>
     * A Column with no starting width opens at an equal share of the Table. A drag can change the
     * width later. A width that is not positive fails the load.
     *
     * @param width the starting width
     */
    public void setWidth(double width) {
        this.width = width;
    }

    /**
     * Returns the starting width.
     *
     * @return the starting width, or {@code 0} when this Column takes an equal share
     */
    public double getWidth() {
        return width == null ? 0 : width;
    }

    /**
     * Returns whether a starting width was set.
     *
     * @return {@code true} when {@link #setWidth(double)} has been called
     */
    boolean hasWidth() {
        return width != null;
    }
}
