package gov.nist.hit.hl7.codeset.adapter.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Small in-memory cache with a fixed time-to-live per entry.
 *
 * Null values are never stored, so a miss or a failed load is retried on the
 * next call rather than remembered. Loads for the same key are serialised so
 * a burst of lookups against a cold key fetches the underlying data once.
 * When the cache is full everything is dropped; the callers keep a handful of
 * keys, so tracking recency is not worth the code.
 */
public final class TtlCache<K, V> {

    private final long ttlMillis;
    private final int maxEntries;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final Map<K, Object> loadLocks = new ConcurrentHashMap<>();

    public TtlCache(long ttlMillis, int maxEntries) {
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
    }

    public V get(K key) {
        Entry<V> e = entries.get(key);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() - e.createdAt >= ttlMillis) {
            entries.remove(key, e);
            return null;
        }
        return e.value;
    }

    public void put(K key, V value) {
        if (value == null) {
            return;
        }
        if (entries.size() >= maxEntries) {
            entries.clear();
        }
        entries.put(key, new Entry<>(value));
    }

    /**
     * Returns the fresh value for the key, loading and storing it if needed.
     * A loader that returns null or throws leaves nothing behind.
     */
    public V computeIfAbsent(K key, Supplier<V> loader) {
        V value = get(key);
        if (value != null) {
            return value;
        }
        Object lock = loadLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            value = get(key);
            if (value != null) {
                return value;
            }
            value = loader.get();
            put(key, value);
            return value;
        }
    }

    public void clear() {
        entries.clear();
    }

    private static final class Entry<V> {
        final V value;
        final long createdAt = System.currentTimeMillis();

        Entry(V value) {
            this.value = value;
        }
    }
}
