package de.neitzel.grid;

import java.io.IOException;
import java.util.Objects;

/** Blocking store port. The configuration service calls it off the UI thread. */
public interface GridSetupStore {
    record Key(String gridId, String context) {
        public Key {
            Checks.id(gridId);
            if (context != null) Checks.id(context);
        }
    }

    record Stored(GridSetup setup, String revision) {
        public Stored {
            Objects.requireNonNull(revision);
        }
    }

    Stored load(Key key) throws IOException;

    /**
     * Revision alone must remain readable even when setup decoding fails, for explicit recovery.
     */
    String revision(Key key) throws IOException;

    /** Atomically compare revision and replace the entire setup; never a blind overwrite. */
    String save(Key key, String expectedRevision, GridSetup setup) throws IOException;

    final class Conflict extends IOException {
        public Conflict() {
            super(
                    "Saved grid setup changed in another application instance. Reload or explicitly"
                            + " overwrite.");
        }
    }
}
