package de.neitzel.fx.example.preview;

import de.neitzel.fxdesigner.preview.PreviewSession;
import de.neitzel.injection.ApplicationContext;

/** The sample owns no timers, network connections or background work to release. */
record SampleSession<M>(M model, ApplicationContext context)
        implements PreviewSession<M, ApplicationContext> {
    @Override
    public void close() {}
}
