package gov.nist.hit.hl7.codeset.adapter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Keeps the serialized JSON of whole-codeset responses in memory.
 *
 * Building a large value set (PHVS_Disease_CDC is ~81k codes, 13 MB of JSON)
 * costs seconds of Mongo reads and Jackson work, and the certification tools
 * request their bound sets on every single validation. The content only
 * changes when a new vocabulary export lands, so the finished bytes are kept
 * for a TTL and rebuilt on the first request after it expires. The fleet
 * binds against a handful of sets; if something enumerates far past that,
 * or past the byte budget, everything is dropped rather than tracked.
 */
@Component
public class CodesetResponseCache {

    private static final int MAX_ENTRIES = 64;
    private static final long MAX_BYTES = 256L * 1024 * 1024;

    private final TtlCache<String, byte[]> entries;

    public CodesetResponseCache(@Value("${codeset.response-cache.ttl-hours:6}") long ttlHours) {
        this.entries = new TtlCache<>(ttlHours * 3_600_000L, MAX_ENTRIES, body -> body.length, MAX_BYTES);
    }

    public byte[] get(String key) {
        return entries.get(key);
    }

    public void put(String key, byte[] body) {
        entries.put(key, body);
    }

    public void clear() {
        entries.clear();
    }
}
