package gov.nist.hit.hl7.codeset.adapter.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TtlCacheTest {

    @Test
    public void returnsWhatWasStored() {
        TtlCache<String, String> cache = new TtlCache<>(3_600_000L, 64);
        cache.put("k", "v");
        assertEquals("v", cache.get("k"));
    }

    @Test
    public void missesOnUnknownKey() {
        TtlCache<String, String> cache = new TtlCache<>(3_600_000L, 64);
        assertNull(cache.get("nope"));
    }

    @Test
    public void expiresWhenTtlIsZero() {
        TtlCache<String, String> cache = new TtlCache<>(0L, 64);
        cache.put("k", "v");
        assertNull(cache.get("k"));
    }

    @Test
    public void loaderRunsOnceWhileTheEntryIsFresh() {
        TtlCache<String, Object> cache = new TtlCache<>(3_600_000L, 64);
        AtomicInteger loads = new AtomicInteger();
        Object first = cache.computeIfAbsent("k", () -> { loads.incrementAndGet(); return new Object(); });
        Object second = cache.computeIfAbsent("k", () -> { loads.incrementAndGet(); return new Object(); });
        assertSame(first, second);
        assertEquals(1, loads.get());
    }

    @Test
    public void nullResultsAreNotCached() {
        TtlCache<String, Object> cache = new TtlCache<>(3_600_000L, 64);
        AtomicInteger loads = new AtomicInteger();
        assertNull(cache.computeIfAbsent("k", () -> { loads.incrementAndGet(); return null; }));
        assertNull(cache.computeIfAbsent("k", () -> { loads.incrementAndGet(); return null; }));
        assertEquals(2, loads.get());
    }

    @Test
    public void loaderFailuresAreNotCachedAndPropagate() {
        TtlCache<String, Object> cache = new TtlCache<>(3_600_000L, 64);
        AtomicInteger loads = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> cache.computeIfAbsent("k", () -> { loads.incrementAndGet(); throw new IllegalStateException("cdc down"); }));
        Object value = cache.computeIfAbsent("k", () -> { loads.incrementAndGet(); return "ok"; });
        assertEquals("ok", value);
        assertEquals(2, loads.get());
    }

    @Test
    public void dropsEverythingWhenTheWeightBudgetIsExceeded() {
        TtlCache<String, String> cache = new TtlCache<>(3_600_000L, 64, String::length, 10);
        cache.put("a", "12345");
        cache.put("b", "1234");
        assertEquals("12345", cache.get("a"));
        cache.put("c", "12");
        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertEquals("12", cache.get("c"));
    }

    @Test
    public void dropsEverythingWhenFull() {
        TtlCache<String, String> cache = new TtlCache<>(3_600_000L, 2);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        assertNull(cache.get("a"));
        assertEquals("3", cache.get("c"));
    }
}
