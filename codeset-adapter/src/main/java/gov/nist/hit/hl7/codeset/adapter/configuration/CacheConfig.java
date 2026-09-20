package gov.nist.hit.hl7.codeset.adapter.configuration;

import gov.nist.hit.hl7.codeset.adapter.service.CodeIndexCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /**
     * The code index holds the same data as the serialized whole-set cache,
     * so it lives for the same time.
     */
    @Bean
    public CodeIndexCache codeIndexCache(@Value("${codeset.response-cache.ttl-hours:6}") long ttlHours) {
        return new CodeIndexCache(TimeUnit.HOURS.toMillis(ttlHours));
    }
}
