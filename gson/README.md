# Gson helpers

This module supplies existing Gson configuration/type adapters and optional DataGrid setup JSON support.

`de.neitzel.gson.grid.GridSetupJson` encodes explicit format-version-1 and format-version-2 documents with independently versioned filter codecs. It depends on neutral `data-grid` contracts, not JavaFX. See [saved setups](../fx/docs/saved-setups.md) for schema behavior, custom codecs and application-owned storage. The `fx` and `data-grid` modules do not depend on this JSON implementation.

Use the repository's declared Java 25/Maven wrapper baseline. Run the Gson tests and affected data-grid/FX integration tests when changing the saved setup schema.

`GridSetupFiles.forApplication(name)` creates the optional application service and
revision-aware JSON file store. It reuses core application-directory resolution;
construction creates no files. FX attachment, failure/conflict recovery and lifecycle
are documented in the [layout/persistence guide](../fx/docs/data-grid-layout.md).
