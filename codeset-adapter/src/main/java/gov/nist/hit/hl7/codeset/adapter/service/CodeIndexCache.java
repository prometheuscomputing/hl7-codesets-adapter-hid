package gov.nist.hit.hl7.codeset.adapter.service;

import gov.nist.hit.hl7.codeset.adapter.model.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Holds whole code set versions in memory and answers point lookups ("is
 * code X in this version") from them.
 *
 * The validation engines ask one code at a time, several times per message.
 * Rather than resolve each one through PHIN VADS or a collection scan, the
 * whole set is loaded once per key and kept both in its original order (for
 * whole-set responses) and as a map from code value to the codes carrying
 * that value (a value can appear under more than one code system). A lookup
 * is then an exact map hit; a code that is not in the set gets an empty
 * answer without leaving the process.
 *
 * Memory is bounded by a total-codes budget rather than a set count, because
 * the API is open and a caller could otherwise pull in large sets at will.
 */
public class CodeIndexCache {

    private static final Logger log = LoggerFactory.getLogger(CodeIndexCache.class);
    private static final int MAX_SETS = 64;
    /** Roughly three times what the suites bind to (~180k codes). */
    static final long DEFAULT_MAX_CODES = 600_000L;

    private final TtlCache<String, Entry> index;

    public CodeIndexCache(long ttlMillis) {
        this(ttlMillis, DEFAULT_MAX_CODES);
    }

    public CodeIndexCache(long ttlMillis, long maxCodes) {
        this.index = new TtlCache<>(ttlMillis, MAX_SETS, e -> e.all.size(), maxCodes);
    }

    /**
     * Every code of the set, in the order the loader produced them.
     *
     * @param key            identifies the code set version (provider|id|version)
     * @param wholeSetLoader produces every code of the set; only used when the
     *                       key is not indexed yet
     */
    public List<Code> all(String key, Supplier<List<Code>> wholeSetLoader) {
        Entry entry = entry(key, wholeSetLoader);
        return entry == null ? Collections.emptyList() : entry.all;
    }

    /**
     * The codes whose value is exactly {@code match}; empty when the set does
     * not contain it.
     */
    public List<Code> lookup(String key, String match, Supplier<List<Code>> wholeSetLoader) {
        Entry entry = entry(key, wholeSetLoader);
        if (entry == null) {
            return Collections.emptyList();
        }
        return entry.byValue.getOrDefault(match, Collections.emptyList());
    }

    private Entry entry(String key, Supplier<List<Code>> wholeSetLoader) {
        return index.computeIfAbsent(key, () -> build(key, wholeSetLoader.get()));
    }

    private static Entry build(String key, List<Code> codes) {
        if (codes == null || codes.isEmpty()) {
            // An empty set is more likely a failed fetch than a real answer;
            // do not pin it in memory.
            return null;
        }
        long started = System.currentTimeMillis();
        Map<String, List<Code>> byValue = new HashMap<>(codes.size() * 2);
        for (Code code : codes) {
            byValue.computeIfAbsent(code.getValue(), v -> new ArrayList<>(1)).add(code);
        }
        byValue.replaceAll((value, list) -> Collections.unmodifiableList(list));
        log.info("Indexed {} codes for {} in {} ms", codes.size(), key, System.currentTimeMillis() - started);
        return new Entry(Collections.unmodifiableList(new ArrayList<>(codes)), byValue);
    }

    private static final class Entry {
        final List<Code> all;
        final Map<String, List<Code>> byValue;

        Entry(List<Code> all, Map<String, List<Code>> byValue) {
            this.all = all;
            this.byValue = byValue;
        }
    }
}
