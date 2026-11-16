package de.neitzel.grid;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable portable checklist values; missing is represented separately by ValueFilter. */
public sealed interface FilterValue {
    record Text(String value) implements FilterValue {
        public Text {
            Objects.requireNonNull(value);
        }
    }

    record IntegerValue(long value) implements FilterValue {}

    record Decimal(BigDecimal value) implements FilterValue {
        public Decimal {
            Objects.requireNonNull(value);
            value = value.stripTrailingZeros();
        }
    }

    record BooleanValue(boolean value) implements FilterValue {}
}
