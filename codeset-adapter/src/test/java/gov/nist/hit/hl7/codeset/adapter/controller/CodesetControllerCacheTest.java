package gov.nist.hit.hl7.codeset.adapter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.hit.hl7.codeset.adapter.model.VersionDetails;
import gov.nist.hit.hl7.codeset.adapter.model.VersionMetadata;
import gov.nist.hit.hl7.codeset.adapter.model.request.CodesetSearchCriteria;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodeResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetResponse;
import gov.nist.hit.hl7.codeset.adapter.service.CodesetResponseCache;
import gov.nist.hit.hl7.codeset.adapter.serviceImpl.CodesetServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The provider answers an outage with an empty code list. That answer must
 * not be kept for the cache TTL, or a failed boot-time warm-up would serve
 * "no codes" for hours.
 */
public class CodesetControllerCacheTest {

    private static final String OID = "2.16.840.1.114222.4.11.909";

    private static CodesetResponse response(List<CodeResponse> codes) {
        CodesetResponse r = new CodesetResponse();
        r.setId(OID);
        r.setVersion(new VersionDetails("7", new Date()));
        r.setLatestStableVersion(new VersionMetadata("7", new Date()));
        r.setCodes(codes);
        return r;
    }

    @Test
    public void emptyWholeSetIsNotCachedAndIsFetchedAgain() throws Exception {
        CodesetServiceImpl service = mock(CodesetServiceImpl.class);
        CodesetResponseCache cache = new CodesetResponseCache(6);
        when(service.getCodeset(eq("phinvads"), eq(OID), any()))
                .thenReturn(response(List.of()))
                .thenReturn(response(List.of(new CodeResponse("A00", "ICD10", "x", false, null, null))));
        CodesetController controller = new CodesetController(service, cache, new ObjectMapper());

        controller.getCodeset("phinvads", OID, new CodesetSearchCriteria());
        assertNull(cache.get("phinvads|" + OID + "|latest"), "an empty set must not be kept");

        controller.getCodeset("phinvads", OID, new CodesetSearchCriteria());
        assertNotNull(cache.get("phinvads|" + OID + "|latest"), "a real set is kept");
        verify(service, times(2)).getCodeset(eq("phinvads"), eq(OID), any());
    }
}
