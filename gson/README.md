# Gson helpers

This module supplies existing Gson configuration/type adapters and optional DataGrid setup JSON support.

`de.neitzel.gson.grid.GridSetupJson` encodes explicit format-version-1 documents with independently versioned filter codecs. It depends on neutral `data-grid` contracts, not JavaFX. See [saved setups](../fx/docs/saved-setups.md) for schema behavior, custom codecs and application-owned storage. The `fx` and `data-grid` modules do not depend on this JSON implementation.

Use the repository's declared Java 25/Maven wrapper baseline. Run the Gson tests and affected data-grid/FX integration tests when changing the saved setup schema.
