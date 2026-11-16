package de.neitzel.grid;

import java.math.BigDecimal;
import java.util.Objects;

/** Exact numeric comparison. Column/domain precision validation belongs to the source mapping. */
public record NumericFilter(
        String columnId, Operator operator, BigDecimal operand, BigDecimal upper)
        implements FilterCondition {
    public enum Operator {
        EQUALS,
        LESS,
        LESS_OR_EQUAL,
        GREATER,
        GREATER_OR_EQUAL,
        RANGE
    }

    public NumericFilter {
        Checks.id(columnId);
        Objects.requireNonNull(operator);
        Objects.requireNonNull(operand);
        if (operator == Operator.RANGE) {
            Objects.requireNonNull(upper);
            if (operand.compareTo(upper) > 0) throw new IllegalArgumentException("Reversed range");
        } else if (upper != null)
            throw new IllegalArgumentException("Upper bound is only valid for a range");
    }

    public String typeId() {
        return "neitzel.number";
    }

    public boolean matches(BigDecimal value) {
        if (value == null) return false;
        int c = value.compareTo(operand);
        return switch (operator) {
            case EQUALS -> c == 0;
            case LESS -> c < 0;
            case LESS_OR_EQUAL -> c <= 0;
            case GREATER -> c > 0;
            case GREATER_OR_EQUAL -> c >= 0;
            case RANGE -> c >= 0 && value.compareTo(upper) <= 0;
        };
    }
}
