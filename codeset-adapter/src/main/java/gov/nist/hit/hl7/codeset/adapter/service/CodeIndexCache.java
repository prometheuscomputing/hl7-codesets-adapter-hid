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
 * Answers point lookups ("is code X in this code set version") from memory.
 *
 * The validation engines ask one code at a time, several times per message.
 * Rather than resolve each one through PHIN VADS or a collection scan, the
 * whole set is loaded once per key and turned into a map from code value to
 * the codes carrying that value (a value can appear under more than one code
 * system). A lookup is then an exact map hit; a code that is not in the set
 * gets an empty answer without leaving the process.
 */
public class CodeIndexCache {

    private static final Logger log = LoggerFactory.getLogger(CodeIndexCache.class);
    private static final int MAX_SETS = 64;

    private final TtlCache<String, Map<String, List<Code>>> index;

    public CodeIndexCache(long ttlMillis) {
        this.index = new TtlCache<>(ttlMillis, MAX_SETS);
    }

    /**
     * @param key            identifies the code set version (provider|id|version)
     * @param match          the exact code value asked for
     * @param wholeSetLoader produces every code of the set; only used when the
     *                       key is not indexed yet
     */
    public List<Code> lookup(String key, String match, Supplier<List<Code>> wholeSetLoader) {
        Map<String, List<Code>> byValue = index.computeIfAbsent(key, () -> build(key, wholeSetLoader.get()));
        if (byValue == null) {
            return Collections.emptyList();
        }
        return byValue.getOrDefault(match, Collections.emptyList());
    }

    public void clear() {
        index.clear();
    }

    private static Map<String, List<Code>> build(String key, List<Code> codes) {
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
        return byValue;
    }
}
