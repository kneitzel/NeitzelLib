package de.neitzel.grid;

final class Checks {
    private Checks() {}

    static String id(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Identifier must not be blank");
        return value;
    }
}
