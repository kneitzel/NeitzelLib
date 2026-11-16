package de.neitzel.fx.grid.filter;

import de.neitzel.grid.FilterCandidates;

import java.math.BigDecimal;
import java.util.*;

/** Developer configuration. Bounds express the actual numeric domain, without rounding. */
public record FilterColumn(
        String editorId,
        Locale locale,
        boolean caseSensitive,
        boolean integer,
        BigDecimal minimum,
        BigDecimal maximum,
        Checklist checklist,
        FilterCandidates.Provider candidates) {
    public enum Checklist {
        INHERIT,
        DISABLED,
        CURRENT_PAGE,
        SOURCE_WIDE
    }

    public FilterColumn {
        Objects.requireNonNull(editorId);
        Objects.requireNonNull(locale);
        Objects.requireNonNull(checklist);
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0)
            throw new IllegalArgumentException("Reversed numeric domain");
        if ((checklist == Checklist.CURRENT_PAGE || checklist == Checklist.SOURCE_WIDE)
                && candidates == null)
            throw new IllegalArgumentException(
                    "Enabled checklist requires a developer-supplied provider");
    }

    public static FilterColumn text() {
        return new FilterColumn(
                "neitzel.text",
                Locale.getDefault(),
                false,
                false,
                null,
                null,
                Checklist.INHERIT,
                null);
    }

    public static FilterColumn number(
            Locale locale, boolean integer, BigDecimal minimum, BigDecimal maximum) {
        return new FilterColumn(
                "neitzel.number",
                locale,
                false,
                integer,
                minimum,
                maximum,
                Checklist.INHERIT,
                null);
    }

    public FilterColumn withChecklist(Checklist mode, FilterCandidates.Provider provider) {
        return new FilterColumn(
                editorId, locale, caseSensitive, integer, minimum, maximum, mode, provider);
    }
}
