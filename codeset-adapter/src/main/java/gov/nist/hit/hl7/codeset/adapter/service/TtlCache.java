package gov.nist.hit.hl7.codeset.adapter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/**
 * Small in-memory cache with a fixed time-to-live per entry.
 *
 * Null values are never stored, so a miss or a failed load is retried on the
 * next call rather than remembered. Loads for the same key are serialised
 * on a fixed set of striped locks, so a burst of lookups against a cold key
 * fetches the underlying data once and unknown keys cannot grow anything;
 * the price is that two cold keys on the same stripe (1 in 64) load one
 * after the other. The cache is bounded by entry count and, optionally, by
 * a weight budget (bytes, codes, whatever the caller measures); when either
 * would be exceeded everything is dropped and the new entry stored, so a
 * single entry larger than the budget is still kept on its own. The
 * callers keep a handful of keys, so tracking recency is not worth the code.
 */
public final class TtlCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(TtlCache.class);
    private static final int STRIPES = 64;

    private final long ttlMillis;
    private final int maxEntries;
    private final ToLongFunction<V> weigher;
    private final long maxWeight;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final Object[] stripes = new Object[STRIPES];
    private long weight;

    public TtlCache(long ttlMillis, int maxEntries) {
        this(ttlMillis, maxEntries, v -> 0L, Long.MAX_VALUE);
    }

    public TtlCache(long ttlMillis, int maxEntries, ToLongFunction<V> weigher, long maxWeight) {
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
        this.weigher = weigher;
        this.maxWeight = maxWeight;
        for (int i = 0; i < STRIPES; i++) {
            stripes[i] = new Object();
        }
    }

    public V get(K key) {
        Entry<V> e = entries.get(key);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() - e.createdAt >= ttlMillis) {
            evict(key, e);
            return null;
        }
        return e.value;
    }

    public void put(K key, V value) {
        if (value == null) {
            return;
        }
        long w = weigher.applyAsLong(value);
        synchronized (this) {
            Entry<V> previous = entries.get(key);
            long previousWeight = previous == null ? 0 : previous.weight;
            boolean full = previous == null && entries.size() >= maxEntries;
            boolean overBudget = weight - previousWeight + w > maxWeight;
            if (full || overBudget) {
                log.warn("Cache dropped {} entries (weight {}) to admit an entry of weight {}; budget {} entries / {} weight. Raise the budget if this repeats.",
                        entries.size(), weight, w, maxEntries, maxWeight);
                entries.clear();
                weight = 0;
                previousWeight = 0;
            }
            entries.put(key, new Entry<>(value, w));
            weight += w - previousWeight;
        }
    }

    /** Forgets one key so the next call loads it again. */
    public synchronized void invalidate(K key) {
        Entry<V> e = entries.remove(key);
        if (e != null) {
            weight -= e.weight;
        }
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
        synchronized (stripes[Math.floorMod(key.hashCode(), STRIPES)]) {
            value = get(key);
            if (value != null) {
                return value;
            }
            value = loader.get();
            put(key, value);
            return value;
        }
    }

    public synchronized void clear() {
        entries.clear();
        weight = 0;
    }

    private synchronized void evict(K key, Entry<V> e) {
        if (entries.remove(key, e)) {
            weight -= e.weight;
        }
    }

    private static final class Entry<V> {
        final V value;
        final long weight;
        final long createdAt = System.currentTimeMillis();

        Entry(V value, long weight) {
            this.value = value;
            this.weight = weight;
        }
    }
}
