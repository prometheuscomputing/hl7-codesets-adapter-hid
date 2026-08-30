package gov.nist.hit.hl7.codeset.adapter.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CodesetResponseCacheTest {

    @Test
    public void returnsWhatWasStored() {
        CodesetResponseCache cache = new CodesetResponseCache(6);
        byte[] body = "{\"name\":\"PHVS_Disease_CDC\"}".getBytes();
        cache.put("phinvads|2.16.840.1.114222.4.11.909|latest", body);
        assertArrayEquals(body, cache.get("phinvads|2.16.840.1.114222.4.11.909|latest"));
    }

    @Test
    public void missesOnUnknownKeyAndAfterClear() {
        CodesetResponseCache cache = new CodesetResponseCache(6);
        assertNull(cache.get("phinvads|nope|latest"));
        cache.put("k", new byte[]{1});
        cache.clear();
        assertNull(cache.get("k"));
    }

    @Test
    public void expiresAfterTtl() {
        CodesetResponseCache cache = new CodesetResponseCache(0);
        cache.put("k", new byte[]{1});
        assertNull(cache.get("k"));
    }

    @Test
    public void versionedAndLatestKeysAreDistinct() {
        CodesetResponseCache cache = new CodesetResponseCache(6);
        cache.put("phinvads|x|6", new byte[]{6});
        assertNull(cache.get("phinvads|x|latest"));
    }
}
