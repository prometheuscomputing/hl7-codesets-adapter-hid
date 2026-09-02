package gov.nist.hit.hl7.codeset.adapter.service;

import gov.nist.hit.hl7.codeset.adapter.model.Code;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CodeIndexCacheTest {

    private static final String KEY = "phinvads|2.16.840.1.114222.4.11.909|latest";

    private static Code code(String value, String system) {
        return new Code(value, "desc " + value, system, null);
    }

    private static List<Code> wholeSet() {
        return List.of(code("A00", "ICD10"), code("A01", "ICD10"), code("A01", "SCT"), code("B99", "ICD10"));
    }

    @Test
    public void exactMatchReturnsEveryCodeWithThatValueInSetOrder() {
        CodeIndexCache index = new CodeIndexCache(3_600_000L);
        List<Code> hits = index.lookup(KEY, "A01", CodeIndexCacheTest::wholeSet);
        assertEquals(2, hits.size());
        assertEquals("ICD10", hits.get(0).getCodeSystem());
        assertEquals("SCT", hits.get(1).getCodeSystem());
    }

    @Test
    public void matchIsExactNotPrefix() {
        CodeIndexCache index = new CodeIndexCache(3_600_000L);
        assertTrue(index.lookup(KEY, "A0", CodeIndexCacheTest::wholeSet).isEmpty());
        assertTrue(index.lookup(KEY, "a01", CodeIndexCacheTest::wholeSet).isEmpty());
    }

    @Test
    public void wholeSetIsLoadedOnceForManyLookupsIncludingMisses() {
        CodeIndexCache index = new CodeIndexCache(3_600_000L);
        AtomicInteger loads = new AtomicInteger();
        for (String m : new String[]{"A00", "ZZZ", "B99", "ZZZ"}) {
            index.lookup(KEY, m, () -> { loads.incrementAndGet(); return wholeSet(); });
        }
        assertEquals(1, loads.get());
    }

    @Test
    public void returnedCodesAreTheLoadedObjects() {
        CodeIndexCache index = new CodeIndexCache(3_600_000L);
        List<Code> set = wholeSet();
        List<Code> hits = index.lookup(KEY, "B99", () -> set);
        assertSame(set.get(3), hits.get(0));
    }

    @Test
    public void differentSetsAreIndexedSeparately() {
        CodeIndexCache index = new CodeIndexCache(3_600_000L);
        index.lookup(KEY, "A00", CodeIndexCacheTest::wholeSet);
        List<Code> other = index.lookup("phinvads|other|latest", "A00", () -> List.of(code("X", "ICD10")));
        assertTrue(other.isEmpty());
    }

    @Test
    public void indexExpiresWithTheTtl() {
        CodeIndexCache index = new CodeIndexCache(0L);
        AtomicInteger loads = new AtomicInteger();
        index.lookup(KEY, "A00", () -> { loads.incrementAndGet(); return wholeSet(); });
        index.lookup(KEY, "A00", () -> { loads.incrementAndGet(); return wholeSet(); });
        assertEquals(2, loads.get());
    }

    @Test
    public void wholeSetAndLookupsShareOneLoad() {
        CodeIndexCache index = new CodeIndexCache(3_600_000L);
        AtomicInteger loads = new AtomicInteger();
        List<Code> set = wholeSet();
        List<Code> all = index.all(KEY, () -> { loads.incrementAndGet(); return set; });
        List<Code> hit = index.lookup(KEY, "B99", () -> { loads.incrementAndGet(); return wholeSet(); });
        assertEquals(4, all.size());
        assertSame(set.get(3), hit.get(0));
        assertEquals(1, loads.get());
    }

    @Test
    public void wholeSetKeepsTheLoadedOrder() {
        CodeIndexCache index = new CodeIndexCache(3_600_000L);
        List<Code> all = index.all(KEY, CodeIndexCacheTest::wholeSet);
        assertEquals(List.of("A00", "A01", "A01", "B99"), all.stream().map(Code::getValue).toList());
    }

    @Test
    public void tooManyCodesInTotalDropsTheIndex() {
        CodeIndexCache index = new CodeIndexCache(3_600_000L, 6);
        AtomicInteger loads = new AtomicInteger();
        index.lookup("set-1", "A00", () -> { loads.incrementAndGet(); return wholeSet(); });
        index.lookup("set-2", "A00", () -> { loads.incrementAndGet(); return wholeSet(); });
        index.lookup("set-1", "A00", () -> { loads.incrementAndGet(); return wholeSet(); });
        assertEquals(3, loads.get(), "8 codes exceed a budget of 6, so set-1 had to be reloaded");
    }

    @Test
    public void emptySetsAreNotKept() {
        CodeIndexCache index = new CodeIndexCache(3_600_000L);
        AtomicInteger loads = new AtomicInteger();
        index.lookup(KEY, "A00", () -> { loads.incrementAndGet(); return List.of(); });
        index.lookup(KEY, "A00", () -> { loads.incrementAndGet(); return List.of(); });
        assertEquals(2, loads.get());
    }
}
