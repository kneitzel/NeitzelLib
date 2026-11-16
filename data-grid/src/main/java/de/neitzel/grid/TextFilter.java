package de.neitzel.grid;

import java.util.Locale;
import java.util.Objects;

/**
 * Literal text comparison; missing values never match. No accent removal or Unicode normalization.
 */
public record TextFilter(String columnId, Operator operator, String operand, boolean caseSensitive)
        implements FilterCondition {
    public enum Operator {
        EQUALS,
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH
    }

    public TextFilter {
        Checks.id(columnId);
        Objects.requireNonNull(operator);
        Objects.requireNonNull(operand);
        if (operand.isEmpty() && operator != Operator.EQUALS)
            throw new IllegalArgumentException("Empty search operand");
    }

    public String typeId() {
        return "neitzel.text";
    }

    public boolean matches(String value) {
        if (value == null) return false;
        String left = caseSensitive ? value : value.toLowerCase(Locale.ROOT),
                right = caseSensitive ? operand : operand.toLowerCase(Locale.ROOT);
        return switch (operator) {
            case EQUALS -> left.equals(right);
            case CONTAINS -> left.contains(right);
            case STARTS_WITH -> left.startsWith(right);
            case ENDS_WITH -> left.endsWith(right);
        };
    }
}
