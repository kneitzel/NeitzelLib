package de.neitzel.fx.injection.fixture;

import jakarta.inject.Singleton;
import java.util.IdentityHashMap;
import java.util.Map;

@Singleton
public final class Lifecycle {
    private final Map<Object, Integer> counts = new IdentityHashMap<>();

    public void opened(Object instance) { counts.put(instance, 0); }

    public void closed(Object instance) { counts.computeIfPresent(instance, (key, count) -> count + 1); }

    public int closeCount(Object instance) { return counts.get(instance); }
}
