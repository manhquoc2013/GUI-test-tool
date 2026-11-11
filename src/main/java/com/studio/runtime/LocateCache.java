package com.studio.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, page-scoped cache of healed selectors.
 *
 * <p>The previous cache was a static map keyed only by the old selector: a repair learned on one
 * application/page was applied to every other one, and the map grew without limit. Entries now carry
 * a page scope and expire, and the oldest entry is evicted past {@link #MAX_ENTRIES}.</p>
 */
public final class LocateCache {

    static final long TTL_MS = 30L * 60L * 1000L;
    static final int MAX_ENTRIES = 512;

    private record CachedSelector(String selector, long expiresAt) {
    }

    private final LinkedHashMap<String, CachedSelector> entries = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedSelector> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private static String key(String scope, String selector) {
        return (scope != null ? scope : "") + "::" + (selector != null ? selector : "");
    }

    public synchronized String get(String scope, String selector) {
        String id = key(scope, selector);
        CachedSelector entry = entries.get(id);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt() < System.currentTimeMillis()) {
            entries.remove(id);
            return null;
        }
        return entry.selector();
    }

    public synchronized void put(String scope, String selector, String repaired) {
        if (selector == null || selector.isBlank() || repaired == null || repaired.isBlank()) {
            return;
        }
        entries.put(key(scope, selector), new CachedSelector(repaired, System.currentTimeMillis() + TTL_MS));
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    /** Flat snapshot for diagnostics/UI: {@code scope::selector -> repaired}. */
    public synchronized Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        entries.forEach((k, v) -> {
            if (v.expiresAt() >= now) {
                out.put(k, v.selector());
            }
        });
        return Map.copyOf(out);
    }
}
