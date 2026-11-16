package de.neitzel.fx.example.grid;

/** Best-effort release of independent application-owned resources, preserving failures. */
final class DemoResources {
    private DemoResources() {}

    static void closeAll(AutoCloseable... resources) {
        RuntimeException failure = null;
        for (var resource : resources) {
            if (resource == null) continue;
            try {
                resource.close();
            } catch (Exception error) {
                if (failure == null)
                    failure = new IllegalStateException("Could not release demo resources", error);
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
