# Asynchronous tab and floating-window close

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
works through the tab's close control. This example demonstrates individual tab
consent; [main-window shutdown](docking-shutdown.md) adds complete-workspace
consent, pre-close saving and manager replacement.

## Closing a floating window

`requestFloatingWindowClose(windowId)` and the floating Stage's window close
control use one atomic workflow. The manager snapshots the window's Dockables
in layout/tab order and asks each sequentially, including after a veto or failed
check. Every callback receives the same `FLOATING_WINDOW` scope and the owning
floating Stage. No tab is detached before all answers allow closure. Application
saves already completed by a callback remain saved if a later callback vetoes.

The running scope protects the entire window against structural changes.
Duplicate window requests and tab requests covered by that running window
request share its outcome. Independent requests queue in order; a veto does not
cancel the next independent window. Content remains editable while decisions
are pending. There is no blocking wait or nested JavaFX event loop.

After unanimous consent, all affected content is detached and the completed
layout is published once. Then every `onClosed` callback runs, even if an earlier
cleanup fails. `Closed.cleanupFailures()` reports those failures. The scope stays
observable until cleanup finishes. Unknown/already closed floating IDs return
`NoChange`; the main-window ID is rejected by this floating-only entry point.
Main-window and manager shutdown use the same consent and cleanup guarantees;
the [shutdown guide](docking-shutdown.md) describes their snapshot callback and
ownership modes.

The example launcher is
`de.neitzel.fx.example.docking.FloatingCloseDockingMain`. It creates First and
Second in one floating window plus Other in an independent window. Its saves
are in-memory demonstration snapshots in the log, not filesystem writes.

1. Choose **Close Editors**, then **Save and allow** for First and **Keep open**
   for Second. Both editors remain open and First's saved text stays in the log.
2. Close Editors again; veto First. Second is still asked. Answer it to finish
   the request without closing either editor.
3. While a decision is pending, use **Repeat Editors close** and **Close Second
   tab**. They report the same result without additional prompts.
4. During that pending request, choose **Close Other window**. Its prompt starts
   after the Editors request ends, including when Editors was vetoed.
5. Approve both Editors. The window disappears before either release log entry.
   The floating window's native close control follows the same sequence.

The demo's main Stage is only a control panel; closing it hides its demonstration
windows. For an application-wide Quit command, use the
[shutdown workflow](docking-shutdown.md).
