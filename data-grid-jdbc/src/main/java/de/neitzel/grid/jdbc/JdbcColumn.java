package de.neitzel.grid.jdbc;

import de.neitzel.grid.*;

import java.math.*;
import java.util.*;

/** Approved physical identifier and scalar domain. Text keys are maintained by the application. */
public record JdbcColumn(
        String id, String sqlName, Kind kind, String comparisonKey, int precision, int scale) {
    public enum Kind {
        TEXT,
        INTEGER,
        DECIMAL
    }

    public JdbcColumn {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Missing column identity");
        identifier(sqlName);
        Objects.requireNonNull(kind);
        if (kind == Kind.TEXT) identifier(comparisonKey);
        else if (comparisonKey != null)
            throw new IllegalArgumentException("Only text has comparison keys");
        if (kind == Kind.DECIMAL && (precision < 1 || scale < 0 || scale > precision))
            throw new IllegalArgumentException("Invalid decimal domain");
    }

    public static JdbcColumn text(String id, String column, String key) {
        return new JdbcColumn(id, column, Kind.TEXT, key, 0, 0);
    }

    public static JdbcColumn integer(String id, String column) {
        return new JdbcColumn(id, column, Kind.INTEGER, null, 0, 0);
    }

    public static JdbcColumn decimal(String id, String column, int precision, int scale) {
        return new JdbcColumn(id, column, Kind.DECIMAL, null, precision, scale);
    }

    static String identifier(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("Expected an approved simple SQL identifier");
        return name;
    }

    void number(BigDecimal value) {
        if (kind == Kind.INTEGER) value.longValueExact();
        else if (kind == Kind.DECIMAL) {
            if (value.setScale(scale, RoundingMode.UNNECESSARY).precision() > precision)
                throw new IllegalArgumentException("Decimal exceeds " + precision + "," + scale);
        } else
            throw new IllegalArgumentException("Numeric condition requires numeric column " + id);
    }

    Object value(FilterValue value) {
        if (kind == Kind.TEXT && value instanceof FilterValue.Text t) return t.value();
        if (kind == Kind.INTEGER && value instanceof FilterValue.IntegerValue n) return n.value();
        if (kind == Kind.DECIMAL && value instanceof FilterValue.Decimal n) {
            number(n.value());
            return n.value();
        }
        throw new IllegalArgumentException("Checklist type does not match " + id);
    }
}
