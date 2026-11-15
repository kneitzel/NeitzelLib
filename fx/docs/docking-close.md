# Asynchronous tab close

Use `Dockable.setCloseCheck` to supply a `CompletionStage<CloseConsent>`.
The callback receives the request scope and the JavaFX window hosting the
content. It can show a nonblocking confirmation or await a save. Return ALLOW
to close or VETO to keep the content available. A failed check is reported as
a veto with its cause. The manager resumes completion on the FX thread.

The tab's close control, context-menu action and `requestTabClose(id)` use the
same workflow. No tab content is removed while consent is pending. Repeated
requests covering that tab share the pending outcome and do not repeat prompts.
`closingScopeProperty()` reports affected IDs until the request and resource
release finish. Conflicting structural operations return `CLOSE_IN_PROGRESS`;
application prompts and content input can continue.

`setOnClosed` releases resources after an actual close. It is separate from
`setOnDiscarded`, which releases factory-prepared content that never opened.
Moving or vetoing a close calls neither close cleanup nor unused-content cleanup.
A cleanup exception is reported in `CloseResult.Closed.cleanupFailures()`;
it does not undo a completed close. Closing the active tab selects its group's
replacement or the most recently active visible Dockable, or clears the active
value if none is visible.

The `CloseDockingMain` launcher in the example module shows a pending close
without a nested event loop. Choose Close Notes, then Keep open and continue
editing. Request close again and choose Allow close. Until that decision, Notes
remains open; after approval its resource callback runs. The same demonstration
works through the tab's close control. Window-wide shutdown is added by the
later lifecycle slices; this example demonstrates individual tab consent.
