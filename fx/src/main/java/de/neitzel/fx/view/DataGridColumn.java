package de.neitzel.fx.view;

import de.neitzel.fx.grid.filter.FilterColumn;
import de.neitzel.grid.*;

import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

/** Declarative column shared by rendering, local queries, filters, grouping and saved setups. */
@Getter
@Setter
public final class DataGridColumn {
    private String id;
    private String header;
    private String attribute;
    private String groupAttribute;

    @Setter(lombok.AccessLevel.NONE)
    private final java.util.Map<String, String> groupLabels = new java.util.LinkedHashMap<>();

    private double width = 150;
    private double minimumWidth = 80;
    private double maximumWidth = 1200;
    private int fixedPosition = -1;
    private MemoryColumn.Kind kind = MemoryColumn.Kind.TEXT;
    private boolean filterable = true;
    private FilterColumn.Checklist checklist = FilterColumn.Checklist.DISABLED;
    private FilterCandidates.Provider candidates;
    private String type;

    ResultColumn definition() {
        if (kind == null || checklist == null)
            throw new IllegalArgumentException("Column kind and checklist are required");
        return new ResultColumn(id, header, width, minimumWidth, maximumWidth, fixedPosition);
    }

    public Object getValue(Object row) {
        return read(row, attribute == null ? id : attribute);
    }

    static Object read(Object row, String attribute) {
        if (row == null) return null;
        if (attribute == null || attribute.isBlank())
            throw new IllegalArgumentException("Missing row attribute");
        String suffix = Character.toUpperCase(attribute.charAt(0)) + attribute.substring(1);
        for (String name : new String[] {"get" + suffix, "is" + suffix, attribute}) {
            try {
                return row.getClass().getMethod(name).invoke(row);
            } catch (NoSuchMethodException absent) {
                // JavaBeans and record-style scalar accessors are both supported.
            } catch (IllegalAccessException | InvocationTargetException error) {
                throw new IllegalArgumentException(
                        "Could not read row attribute " + attribute, error);
            }
        }
        throw new IllegalArgumentException(
                "No row attribute " + attribute + " on " + row.getClass().getName());
    }

    FilterValue groupValue(Object row) {
        return scalar(groupAttribute == null ? getValue(row) : read(row, groupAttribute));
    }

    String groupLabel(FilterValue value) {
        String display = GroupColumn.display(value);
        return groupLabels.getOrDefault(display, display);
    }

    FilterValue value(Object row) {
        return scalar(getValue(row));
    }

    private FilterValue scalar(Object value) {
        if (value == null) return null;
        return switch (kind) {
            case TEXT -> new FilterValue.Text(value.toString());
            case INTEGER ->
                    new FilterValue.IntegerValue(new BigDecimal(value.toString()).longValueExact());
            case DECIMAL -> new FilterValue.Decimal(new BigDecimal(value.toString()));
            case BOOLEAN -> {
                if (!(value instanceof Boolean bool))
                    throw new IllegalArgumentException("Boolean value required for " + id);
                yield new FilterValue.BooleanValue(bool);
            }
        };
    }

    String schemaType() {
        return type == null
                ? "neitzel." + kind.name().toLowerCase(java.util.Locale.ROOT) + ".v1"
                : type;
    }
}
