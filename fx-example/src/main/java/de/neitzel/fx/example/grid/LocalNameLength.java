package de.neitzel.fx.example.grid;

import de.neitzel.grid.FilterCondition;

/** Deliberately memory-only condition used to demonstrate visible backend rejection. */
public record LocalNameLength(String columnId) implements FilterCondition {
    public String typeId() {
        return "demo.name-length";
    }
}
