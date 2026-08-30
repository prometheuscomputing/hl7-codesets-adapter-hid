package gov.nist.hit.hl7.codeset.adapter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the serialized JSON of whole-codeset responses in memory.
 *
 * Building a large value set (PHVS_Disease_CDC is ~81k codes, 13 MB of JSON)
 * costs seconds of Mongo reads and Jackson work, and the certification tools
 * request their bound sets on every single validation. The content only
 * changes when a new vocabulary export lands, so the finished bytes are kept
 * for a TTL and rebuilt on the first request after it expires.
 */
@Component
public class CodesetResponseCache {

    private static final int MAX_ENTRIES = 64;

    private final long ttlMillis;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public CodesetResponseCache(@Value("${codeset.response-cache.ttl-hours:6}") long ttlHours) {
        this.ttlMillis = ttlHours * 3_600_000L;
    }

    public byte[] get(String key) {
        Entry e = entries.get(key);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() - e.createdAt >= ttlMillis) {
            entries.remove(key, e);
            return null;
        }
        return e.body;
    }

    public void put(String key, byte[] body) {
        // The fleet binds against a handful of sets; if something enumerates
        // far past that, dropping everything is cheaper than tracking LRU.
        if (entries.size() >= MAX_ENTRIES) {
            entries.clear();
        }
        entries.put(key, new Entry(body));
    }

    public void clear() {
        entries.clear();
    }

    private static final class Entry {
        final byte[] body;
        final long createdAt = System.currentTimeMillis();

        Entry(byte[] body) {
            this.body = body;
        }
    }
}
