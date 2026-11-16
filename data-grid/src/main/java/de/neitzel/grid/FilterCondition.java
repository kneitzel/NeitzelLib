package de.neitzel.grid;

/**
 * Immutable column condition. Custom implementations must preserve value semantics and declare a
 * stable type ID.
 */
public interface FilterCondition {
    String columnId();

    String typeId();
}
