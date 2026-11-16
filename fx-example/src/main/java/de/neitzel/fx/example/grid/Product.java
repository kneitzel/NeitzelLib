package de.neitzel.fx.example.grid;

import java.math.BigDecimal;

/** Immutable demo row; no FX-owned state is read on a worker. */
public record Product(long id, String name, String category, Long quantity, BigDecimal price) {}
