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
    public void replacingAKeyDoesNotDoubleCountItsWeight() {
        TtlCache<String, String> cache = new TtlCache<>(3_600_000L, 64, String::length, 10);
        cache.put("a", "123456");
        cache.put("a", "123456");
        cache.put("b", "1234");
        assertEquals("123456", cache.get("a"), "6 + 4 fits the budget of 10 when a is counted once");
        assertEquals("1234", cache.get("b"));
    }

    @Test
    public void invalidateForgetsOneKeyAndReturnsItsWeight() {
        TtlCache<String, String> cache = new TtlCache<>(3_600_000L, 64, String::length, 10);
        cache.put("a", "123456");
        cache.invalidate("a");
        assertNull(cache.get("a"));
        cache.put("b", "1234567890");
        assertEquals("1234567890", cache.get("b"), "the full budget is available again");
    }

    @Test
    public void aBurstOnAColdKeyLoadsOnce() throws Exception {
        TtlCache<String, String> cache = new TtlCache<>(3_600_000L, 64);
        AtomicInteger loads = new AtomicInteger();
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread t = new Thread(() -> {
                try { go.await(); } catch (InterruptedException e) { return; }
                cache.computeIfAbsent("k", () -> {
                    loads.incrementAndGet();
                    try { Thread.sleep(50); } catch (InterruptedException e) { }
                    return "v";
                });
            });
            t.start();
            threads.add(t);
        }
        go.countDown();
        for (Thread t : threads) {
            t.join();
        }
        assertEquals(1, loads.get());
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
