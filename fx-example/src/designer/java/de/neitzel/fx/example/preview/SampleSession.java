package de.neitzel.fx.example.preview;

import de.neitzel.fxdesigner.preview.PreviewSession;
import de.neitzel.fx.view.ViewDependencies;

/** The sample owns no timers, network connections or background work to release. */
record SampleSession<M>(M model, ViewDependencies context)
        implements PreviewSession<M, ViewDependencies> {
    @Override
    public void close() {}
}
