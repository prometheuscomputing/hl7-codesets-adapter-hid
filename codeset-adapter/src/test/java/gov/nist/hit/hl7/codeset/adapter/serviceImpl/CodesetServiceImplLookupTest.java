package gov.nist.hit.hl7.codeset.adapter.serviceImpl;

import gov.nist.hit.hl7.codeset.adapter.model.Code;
import gov.nist.hit.hl7.codeset.adapter.model.Codeset;
import gov.nist.hit.hl7.codeset.adapter.model.CodesetVersion;
import gov.nist.hit.hl7.codeset.adapter.model.Provider;
import gov.nist.hit.hl7.codeset.adapter.model.VersionDetails;
import gov.nist.hit.hl7.codeset.adapter.model.request.CodesetSearchCriteria;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetMetadataResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetVersionMetadataResponse;
import gov.nist.hit.hl7.codeset.adapter.repository.CodesetRepository;
import gov.nist.hit.hl7.codeset.adapter.repository.CodesetVersionRepository;
import gov.nist.hit.hl7.codeset.adapter.service.CodeIndexCache;
import gov.nist.hit.hl7.codeset.adapter.service.ProviderService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A validator asks for one code at a time. Each answer must come from the
 * set the adapter already holds, never from a fresh PHIN VADS search, and
 * the set must be fetched once, not once per question.
 */
public class CodesetServiceImplLookupTest {

    private static final String OID = "2.16.840.1.114222.4.11.909";
    private static final String VERSION = "7";
    private static final String VERSION_ID = "vsv-909-7";

    /** Stands in for PHIN VADS: a match search there is prefix-based, like the real one. */
    private static final class FakeProvider implements ProviderService {
        final List<Code> wholeSet;
        final List<String> getCodesMatches = new ArrayList<>();

        FakeProvider(List<Code> wholeSet) {
            this.wholeSet = wholeSet;
        }

        public List<CodesetMetadataResponse> getCodesets(CodesetSearchCriteria c) { return null; }
        public Provider getProvider() { return new Provider("phinvads", "Phinvads"); }
        public void getCodesetAndSave(String id, String version) { }
        public String getLatestVersion(String id) { return VERSION; }
        public List<Code> getCodes(String id, String version, String match) {
            getCodesMatches.add(match);
            if (match == null) {
                return wholeSet;
            }
            return wholeSet.stream().filter(c -> c.getValue().startsWith(match)).collect(Collectors.toList());
        }
        public CodesetMetadataResponse getCodesetMetadata(String id) { return null; }
        public CodesetVersionMetadataResponse getCodesetVersionMetadata(String id, String version) { return null; }
    }

    private static Code code(String value, String system) {
        Code c = new Code(value, "desc " + value, system, null);
        c.setCodesetversionId(VERSION_ID);
        return c;
    }

    private static List<Code> wholeSet() {
        return List.of(code("A00", "ICD10"), code("A01", "ICD10"), code("A01", "SCT"), code("B99", "ICD10"));
    }

    private static CodesetSearchCriteria criteria(String match) {
        CodesetSearchCriteria c = new CodesetSearchCriteria();
        c.setMatch(match);
        c.setVersion(VERSION);
        return c;
    }

    private static final class Fixture {
        final CodesetRepository codesets = mock(CodesetRepository.class);
        final CodesetVersionRepository versions = mock(CodesetVersionRepository.class);
        final MongoTemplate mongo = mock(MongoTemplate.class);
        final FakeProvider provider;
        final CodesetServiceImpl service;

        Fixture(CodesetVersion.CodesStatus status, List<Code> set) {
            provider = new FakeProvider(set);

            // A fresh response object per call, as the real aggregation gives.
            when(mongo.aggregate(any(Aggregation.class), eq("codeset"), eq(CodesetResponse.class)))
                    .thenAnswer(invocation -> {
                        CodesetResponse fromAggregation = new CodesetResponse();
                        fromAggregation.setId(OID);
                        fromAggregation.setName("PHVS_Disease_CDC");
                        VersionDetails details = new VersionDetails(VERSION, new Date());
                        details.setId(VERSION_ID);
                        fromAggregation.setVersion(details);
                        return new AggregationResults<>(List.of(fromAggregation), new Document());
                    });

            Codeset codeset = new Codeset();
            codeset.setId("cs-909");
            codeset.setIdentifier(OID);
            when(codesets.findByIdentifier(OID)).thenReturn(Optional.of(codeset));

            CodesetVersion cv = new CodesetVersion();
            cv.setId(VERSION_ID);
            cv.setCodesetId("cs-909");
            cv.setVersion(VERSION);
            cv.setCodesStatus(status);
            when(versions.findByCodesetIdAndVersion("cs-909", VERSION)).thenReturn(Optional.of(cv));

            when(mongo.find(any(Query.class), eq(Code.class))).thenReturn(set);

            service = new CodesetServiceImpl(codesets, versions, mongo, List.of(provider), new CodeIndexCache(3_600_000L));
        }
    }

    @Test
    public void largeSetLookupsNeverSearchPhinvadsAndLoadTheSetOnce() throws Exception {
        Fixture f = new Fixture(CodesetVersion.CodesStatus.NOT_NEEDED, wholeSet());

        CodesetResponse first = f.service.getCodeset("phinvads", OID, criteria("A01"));
        CodesetResponse again = f.service.getCodeset("phinvads", OID, criteria("A01"));
        CodesetResponse miss = f.service.getCodeset("phinvads", OID, criteria("A0"));

        assertEquals(List.of("A01", "A01"), first.getCodes().stream().map(c -> c.getValue()).toList());
        assertEquals(List.of("ICD10", "SCT"), first.getCodes().stream().map(c -> c.getCodeSystem()).toList());
        assertEquals("A01", first.getCodeMatchValue());
        assertEquals(2, again.getCodes().size());
        assertTrue(miss.getCodes().isEmpty(), "a prefix is not a code; PHIN VADS search must not be consulted");
        assertEquals("A0", miss.getCodeMatchValue());
        assertEquals(Collections.singletonList((String) null), f.provider.getCodesMatches, "one whole-set fetch, no match searches");
    }

    @Test
    public void storedSetLookupsReadMongoOnceAndNeverFallBackToPhinvads() throws Exception {
        Fixture f = new Fixture(CodesetVersion.CodesStatus.SAVED, wholeSet());

        CodesetResponse hit = f.service.getCodeset("phinvads", OID, criteria("B99"));
        f.service.getCodeset("phinvads", OID, criteria("A00"));
        CodesetResponse miss = f.service.getCodeset("phinvads", OID, criteria("ZZZ"));

        assertEquals(1, hit.getCodes().size());
        assertTrue(miss.getCodes().isEmpty());
        verify(f.mongo, times(1)).find(any(Query.class), eq(Code.class));
        assertTrue(f.provider.getCodesMatches.isEmpty());
    }

    @Test
    public void wholeSetRequestStillReturnsEverything() throws Exception {
        Fixture f = new Fixture(CodesetVersion.CodesStatus.NOT_NEEDED, wholeSet());

        CodesetResponse all = f.service.getCodeset("phinvads", OID, criteria(null));

        assertEquals(4, all.getCodes().size());
        assertEquals(Collections.singletonList((String) null), f.provider.getCodesMatches);
    }
}
