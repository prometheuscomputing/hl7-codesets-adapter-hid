package gov.nist.hit.hl7.codeset.adapter.serviceImpl;

import com.caucho.hessian.client.HessianProxyFactory;
import gov.cdc.vocab.service.VocabService;
import gov.cdc.vocab.service.bean.CodeSystem;
import gov.cdc.vocab.service.bean.ValueSet;
import gov.cdc.vocab.service.bean.ValueSetConcept;
import gov.cdc.vocab.service.bean.ValueSetVersion;
import gov.cdc.vocab.service.dto.input.CodeSystemSearchCriteriaDto;
import gov.cdc.vocab.service.dto.input.ValueSetConceptSearchCriteriaDto;
import gov.cdc.vocab.service.dto.input.ValueSetSearchCriteriaDto;
import gov.cdc.vocab.service.dto.input.ValueSetVersionSearchCriteriaDto;
import gov.cdc.vocab.service.dto.output.ValueSetConceptResultDto;
import gov.cdc.vocab.service.dto.output.ValueSetResultDto;
import gov.cdc.vocab.service.dto.output.ValueSetVersionResultDto;
import gov.nist.hit.hl7.codeset.adapter.exception.NotFoundException;
import gov.nist.hit.hl7.codeset.adapter.model.*;
import gov.nist.hit.hl7.codeset.adapter.model.request.CodesetSearchCriteria;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetMetadataResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetVersionMetadataResponse;
import gov.nist.hit.hl7.codeset.adapter.repository.CodesetRepository;
import gov.nist.hit.hl7.codeset.adapter.repository.CodesetVersionRepository;
import gov.nist.hit.hl7.codeset.adapter.service.ProviderService;
import gov.nist.hit.hl7.codeset.adapter.service.TtlCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.net.ssl.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service

public class PhinvadsServiceImpl implements ProviderService {

    private final VocabService service;
    private static final Logger log = LoggerFactory.getLogger(PhinvadsServiceImpl.class);
    @Autowired
    MongoOperations mongoOps;
    @Autowired(required = false)
    private PhinvadsFallbackService fallbackService;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private final CodesetRepository codesetRepository;

    private final CodesetVersionRepository codesetVersionRepository;

    // PHIN VADS metadata (value set, its versions, the latest version number,
    // code systems) is asked for on every code lookup the validators make but
    // only changes when CDC publishes. Remembering it for a while turns those
    // Hessian round trips into map hits. Misses and failures are never
    // remembered, so an outage recovers on its own.
    private static final int MEMO_ENTRIES = 4096;
    private final TtlCache<String, ValueSet> valueSetMemo;
    private final TtlCache<String, List<ValueSetVersion>> versionsMemo;
    private final TtlCache<String, String> latestVersionMemo;
    private final TtlCache<String, CodeSystem> codeSystemMemo;
    private final Object[] importLocks = new Object[64];
    {
        for (int i = 0; i < importLocks.length; i++) {
            importLocks[i] = new Object();
        }
    }


    @Autowired
    public PhinvadsServiceImpl(CodesetRepository codesetRepository, CodesetVersionRepository codesetVersionRepository,
                               @Value("${codeset.metadata-cache.ttl-hours:24}") long metadataTtlHours) throws NoSuchAlgorithmException, KeyManagementException {
        this(codesetRepository, codesetVersionRepository, createPhinvadsProxy(), metadataTtlHours);
    }

    /**
     * Wiring seam: the PHIN VADS client is passed in so the metadata memo can
     * be exercised without the network.
     */
    PhinvadsServiceImpl(CodesetRepository codesetRepository, CodesetVersionRepository codesetVersionRepository,
                        VocabService service, long metadataTtlHours) {
        this.codesetRepository = codesetRepository;
        this.codesetVersionRepository = codesetVersionRepository;
        this.service = service;
        long ttlMillis = TimeUnit.HOURS.toMillis(metadataTtlHours);
        this.valueSetMemo = new TtlCache<>(ttlMillis, MEMO_ENTRIES);
        this.versionsMemo = new TtlCache<>(ttlMillis, MEMO_ENTRIES);
        this.latestVersionMemo = new TtlCache<>(ttlMillis, MEMO_ENTRIES);
        this.codeSystemMemo = new TtlCache<>(ttlMillis, MEMO_ENTRIES);
    }

    private static VocabService createPhinvadsProxy() throws NoSuchAlgorithmException, KeyManagementException {
        String serviceUrl = "https://phinvads.cdc.gov/vocabService/v2";
        /* Start of Fix */
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }

        }};


        HessianProxyFactory factory = new HessianProxyFactory();
        // Bound every call: a hung CDC socket must not park a loader (and the
        // callers waiting on its key) for good. (3.1.3 has no connect timeout.)
        factory.setReadTimeout(60_000L);
        try {
            SSLContext sc = SSLContext.getInstance("SSL");

            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            /* End of the fix*/
            return (VocabService) factory.create(VocabService.class, serviceUrl);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Bad PHIN VADS service URL: " + serviceUrl, e);
        }
    }

    public VocabService getService() {
        return this.service;
    }

    @PostConstruct
    public void initPhinvads() throws IOException {
        System.out.println("************ INIT PHINVADS VALUESET METADATA");
        try {
            updateCodesets();
        } catch (Exception e) {
            System.out.println("************ Error loading PHINVADS Service");
//            throw new RuntimeException(e);
        }
    }

    // Every 1:00 AM Saturday
//    @Scheduled(cron = "0 0 1 * * SAT")
    public void updateCodesets() throws IOException {
        log.info("Getting codesets from Phinvads Web service {}" + dateFormat.format(new Date()));
        // Step 1: Retrieve all ValueSets
        List<ValueSet> allValueSets = this.service.getAllValueSets().getValueSets();

        // Step 2: Retrieve all ValueSetVersions in one call
        List<ValueSetVersion> allValueSetVersions = this.service.getAllValueSetVersions().getValueSetVersions();

        // Step 3: Prepare response list
        List<CodesetMetadataResponse> valueSetsWithVersions = new ArrayList<>();

        // Step 4: Loop through each ValueSet and populate CodesetMetadataResponse with versions and concept counts
        for (ValueSet valueSet : allValueSets) {
            String oid = valueSet.getOid();
            String name = valueSet.getName();

            // Check DB if Codeset exist
            Codeset codeset = mongoOps.findOne(Query.query(Criteria.where("identifier").is(oid)),
                    Codeset.class);
            if (codeset != null) {
                log.info("Codeset Already exists: " + oid);
            } else {
                log.info("New Codeset found: " + oid);
                codeset = new Codeset();
                codeset.setVersions(new ArrayList<VersionMetadata>());
                codeset.setName(valueSet.getCode());
                codeset.setDescription(valueSet.getName());
                codeset.setIdentifier(valueSet.getOid());
                codeset.setProvider("phinvads");
                codeset.setDateUpdated(valueSet.getStatusDate());
                codeset.setCodeSetVersions(new HashSet<CodesetVersion>());
            }
            codeset = mongoOps.save(codeset);
            List<ValueSetVersion> currentValueSetVersions = allValueSetVersions.stream().filter(version -> oid.equals(version.getValueSetOid())).toList();
            for (ValueSetVersion valueSetVersion : currentValueSetVersions) {
                String versionNumber = String.valueOf(valueSetVersion.getVersionNumber());
                CodesetVersion codesetVersion = mongoOps.findOne(Query.query(Criteria.where("version").is(versionNumber).and("codesetId").is(codeset.getId())),
                        CodesetVersion.class);

                if (codesetVersion != null) {
                    log.info("Codeset version Already exists " + versionNumber);
                    log.info("No action needed");

                } else {
                    log.info("New Codeset version found " + String.valueOf(valueSetVersion.getVersionNumber()));
                    codesetVersion = createNewCodesetVersion(codeset, valueSetVersion, false);
                    mongoOps.save(codeset);

                }
            }

            mongoOps.save(codeset);
        }

    }

    public CodesetVersion createNewCodesetVersion(Codeset codeset, ValueSetVersion valueSetVersion, Boolean saveCodes) throws IOException {
        CodesetVersion codesetVersion = new CodesetVersion();
        codesetVersion.setVersion(String.valueOf(valueSetVersion.getVersionNumber()));
        codesetVersion.setDateUpdated(valueSetVersion.getStatusDate());
        codesetVersion.setCodesetId(codeset.getId());
        codesetVersion.setCodesStatus(CodesetVersion.CodesStatus.PENDING);

        CodesetVersion savedCodesetVersion = mongoOps.save(codesetVersion);

        VersionMetadata versionMetadata = new VersionMetadata(String.valueOf(valueSetVersion.getVersionNumber()), valueSetVersion.getStatusDate());
        codeset.getVersions().add(versionMetadata);
        if (codeset.getLatestVersion() == null) {
            codeset.setLatestVersion(versionMetadata);
        } else {
            if (valueSetVersion.getVersionNumber() > Integer.parseInt(codeset.getLatestVersion().getVersion())) {
                codeset.setLatestVersion(versionMetadata);
            }
        }

        if(saveCodes){
            // Retrieve concept for the version
            List<ValueSetConcept> valueSetConcepts = this.service
                    .getValueSetConceptsByValueSetVersionId(valueSetVersion.getId(), 1, Integer.MAX_VALUE)
                    .getValueSetConcepts();
            if (valueSetConcepts.size() > 500) {
                savedCodesetVersion.setCodesStatus(CodesetVersion.CodesStatus.NOT_NEEDED);
            } else {
                // Get code systems and save all codes
                Set<String> codeSystemOids = new HashSet<>();
                Map<String, CodeSystem> uniqueIdCodeSystemMap = new HashMap<>();
                List<Code> codes = new ArrayList<Code>();
                for (ValueSetConcept pcode : valueSetConcepts) {
                    if (uniqueIdCodeSystemMap.get(pcode.getCodeSystemOid()) == null) {
                        CodeSystem cs = getCodeSystem(pcode.getCodeSystemOid());
                        uniqueIdCodeSystemMap.put(pcode.getCodeSystemOid(), cs);
                    }
                    Code code = new Code();
                    code.setValue(pcode.getConceptCode());
                    code.setDescription(pcode.getCodeSystemConceptName());
                    code.setComments(pcode.getDefinitionText());
//                    code.setUsage("R");
                    code.setCodeSystem(uniqueIdCodeSystemMap.get(pcode.getCodeSystemOid()).getHl70396Identifier());
                    code.setCodesetversionId(savedCodesetVersion.getId());
                    codes.add(code);
                    savedCodesetVersion.setCodesStatus(CodesetVersion.CodesStatus.SAVED);
                }
                mongoOps.insertAll(codes);
            }
        }



        codeset.getCodeSetVersions().add(savedCodesetVersion);
        log.info("Codeset: " + codeset.getIdentifier() + " version: " + String.valueOf(valueSetVersion.getVersionNumber()) + " has been added");
        mongoOps.save(savedCodesetVersion);
        return codesetVersion;
    }

    @Override
    public String getLatestVersion(String id) throws IOException {
        try {
            return latestVersionMemo.computeIfAbsent(id, () -> {
                ValueSetVersionSearchCriteriaDto criteria = new ValueSetVersionSearchCriteriaDto();
                criteria.setOidSearch(true);
                criteria.setSearchText(id);
                criteria.setSearchType(1);
                criteria.setVersionOption(3);
                log.debug("PHIN VADS findValueSetVersions (latest) for {}", id);
                ValueSetVersionResultDto valuesetVersionDto = this.service.findValueSetVersions(criteria, 1, Integer.MAX_VALUE);
                ValueSetVersion valuesetVersion = valuesetVersionDto.getValueSetVersions().stream().filter(v -> v.getValueSetOid().equals(id)).findFirst().orElse(null);
                return (valuesetVersion != null) ? String.valueOf(valuesetVersion.getVersionNumber()) : null;
            });
        } catch (Exception e) {
            log.warn("PHINVADS unreachable for getLatestVersion({}), trying fallbacks: {}", id, e.getMessage());

            // Fallback 1: Check MongoDB for latest version from cached metadata
            Codeset codeset = codesetRepository.findByIdentifier(id).orElse(null);
            if (codeset != null && codeset.getLatestVersion() != null) {
                log.info("Using cached latest version from MongoDB: {} v{}", id, codeset.getLatestVersion().getVersion());
                return codeset.getLatestVersion().getVersion();
            }

            // Fallback 2: Check exported files on disk
            if (fallbackService != null) {
                String fileVersion = fallbackService.findLatestExportedVersion(id);
                if (fileVersion != null) {
                    log.info("Using latest exported version from disk: {} v{}", id, fileVersion);
                    return fileVersion;
                }
            }

            log.error("No version found for {} from any source", id);
            return null;
        }

    }

    @Override
    public List<CodesetMetadataResponse> getCodesets(CodesetSearchCriteria codesetSearchCriteria) throws IOException {
        return null;
    }

    @Override
    public Provider getProvider() {
        return new Provider("phinvads", "Phinvads");
    }

    public ValueSet getValueset(String id)  {
        try {
            return valueSetMemo.computeIfAbsent(id, () -> {
                ValueSetSearchCriteriaDto criteria = new ValueSetSearchCriteriaDto();
                criteria.setOidSearch(true);
                criteria.setSearchText(id);
                criteria.setSearchType(1);
                log.debug("PHIN VADS findValueSets for {}", id);
                ValueSetResultDto valuesetDto = this.service.findValueSets(criteria, 1, 1);
                return valuesetDto.getValueSet();
            });
        } catch (Exception e) {
            log.warn("PHIN VADS findValueSets failed for {}: {}", id, e.getMessage());
            return null;
        }

    }

    public ValueSetVersion getValuesetVersion(String id, String version) {
        ValueSetVersion found = findVersion(getValuesetVersions(id), version);
        if (found == null) {
            // A version the remembered list does not know may have been
            // published since; ask once more before calling it unknown.
            versionsMemo.invalidate(id);
            found = findVersion(getValuesetVersions(id), version);
        }
        return found;
    }

    private static ValueSetVersion findVersion(List<ValueSetVersion> versions, String version) {
        return versions.stream()
                .filter(v -> String.valueOf(v.getVersionNumber()).equals(version))
                .findFirst().orElse(null);
    }

    public List<ValueSetVersion> getValuesetVersions(String id) {
        List<ValueSetVersion> versions = versionsMemo.computeIfAbsent(id, () -> {
            ValueSetVersionSearchCriteriaDto criteria = new ValueSetVersionSearchCriteriaDto();
            criteria.setOidSearch(true);
            criteria.setSearchText(id);
            criteria.setSearchType(1);
            criteria.setVersionOption(1);
            log.debug("PHIN VADS findValueSetVersions for {}", id);
            ValueSetVersionResultDto valuesetDto = this.service.findValueSetVersions(criteria, 1, Integer.MAX_VALUE);
            List<ValueSetVersion> ofThisSet = valuesetDto.getValueSetVersions().stream().filter(v -> v.getValueSetOid().equals(id)).toList();
            // An unknown set is a miss, not a fact worth remembering.
            return ofThisSet.isEmpty() ? null : ofThisSet;
        });
        return versions == null ? Collections.emptyList() : versions;
    }

    public CodeSystem getCodeSystem(String codeSystemOid) {
        return codeSystemMemo.computeIfAbsent(codeSystemOid, () -> {
            CodeSystemSearchCriteriaDto csSearchCritDto = new CodeSystemSearchCriteriaDto();
            csSearchCritDto.setCodeSearch(false);
            csSearchCritDto.setNameSearch(false);
            csSearchCritDto.setOidSearch(true);
            csSearchCritDto.setDefinitionSearch(false);
            csSearchCritDto.setAssigningAuthoritySearch(false);
            csSearchCritDto.setTable396Search(false);
            csSearchCritDto.setSearchType(1);
            csSearchCritDto.setSearchText(codeSystemOid);
            log.debug("PHIN VADS findCodeSystems for {}", codeSystemOid);
            return this.service.findCodeSystems(csSearchCritDto, 1, 5).getCodeSystems().get(0);
        });
    }

    @Override
    public void getCodesetAndSave(String id, String version) throws IOException {
        try {
            // Metadata first, outside any lock: on a cold memo during a CDC
            // outage these calls time out, and callers should do that side
            // by side rather than queue behind one another.
            ValueSet valueset = getValueset(id);
            if (valueset == null) {
                return;
            }
            ValueSetVersion valuesetVersion = getValuesetVersion(id, version);
            if (valuesetVersion == null) {
                return;
            }
            // Two callers preparing the same version at once (a boot-time
            // warm-up and the first request, say) would both find it PENDING
            // and both import its codes. One at a time per version; the
            // status is re-read inside the lock.
            synchronized (importLocks[Math.floorMod((id + "|" + version).hashCode(), importLocks.length)]) {
                saveLocked(id, version, valueset, valuesetVersion);
            }
        } catch (Exception e) {
            log.warn("Could not prepare {} v{} (PHIN VADS metadata or local import): {}", id, version, e.getMessage());
        }
    }

    private void saveLocked(String id, String version, ValueSet valueset, ValueSetVersion valuesetVersion) throws IOException {
        Codeset codeset = codesetRepository.findByIdentifier(id).orElse(null);
        if (codeset == null) {
            codeset = new Codeset();
            codeset.setIdentifier(id);
            codeset.setVersions(new ArrayList<VersionMetadata>());
            codeset.setName(valueset.getCode());
            codeset.setDescription(valueset.getName());
            codeset.setProvider("phinvads");
            codeset.setDateUpdated(valueset.getStatusDate());
            codeset.setCodeSetVersions(new HashSet<CodesetVersion>());
            codeset = mongoOps.save(codeset);
        }
        CodesetVersion codesetVersion = codesetVersionRepository.findByCodesetIdAndVersion(codeset.getId(), version).orElse(null);
        if (codesetVersion == null) {
            codesetVersion = createNewCodesetVersion(codeset, valuesetVersion, true);
            mongoOps.save(codeset);
        } else {
            if (codesetVersion.getCodesStatus() == null || codesetVersion.getCodesStatus().equals(CodesetVersion.CodesStatus.PENDING)) {
                List<ValueSetConcept> valueSetConcepts = this.service
                        .getValueSetConceptsByValueSetVersionId(valuesetVersion.getId(), 1, Integer.MAX_VALUE)
                        .getValueSetConcepts();
                // Get code systems and save all codes
                Set<String> codeSystemOids = new HashSet<>();
                Map<String, CodeSystem> uniqueIdCodeSystemMap = new HashMap<>();
                List<Code> codes = new ArrayList<Code>();
                for (ValueSetConcept pcode : valueSetConcepts) {
                    if (uniqueIdCodeSystemMap.get(pcode.getCodeSystemOid()) == null) {
                        CodeSystem cs = getCodeSystem(pcode.getCodeSystemOid());
                        uniqueIdCodeSystemMap.put(pcode.getCodeSystemOid(), cs);
                    }
                    Code code = new Code();
                    code.setValue(pcode.getConceptCode());
                    code.setDescription(pcode.getCodeSystemConceptName());
                    code.setComments(pcode.getDefinitionText());
//                    code.setUsage("R");
                    code.setCodeSystem(uniqueIdCodeSystemMap.get(pcode.getCodeSystemOid()).getHl70396Identifier());
                    code.setCodesetversionId(codesetVersion.getId());
                    codes.add(code);
                    codesetVersion.setCodesStatus(CodesetVersion.CodesStatus.SAVED);
                }
                mongoOps.insertAll(codes);
                mongoOps.save(codesetVersion);
            }
        }
    }

    @Override
    public List<Code> getCodes(String id, String version, String match) throws IOException {
        try {
            ValueSetVersion valuesetVersion = getValuesetVersion(id, version);
            if (valuesetVersion == null) {
                // PHINVADS responded but has no such version, not an outage, so don't serve local data
                log.warn("PHINVADS has no version {} for codeset {}; returning no codes", version, id);
                return new ArrayList<>();
            }

            List<ValueSetConcept> valueSetConcepts = new ArrayList<>();
            if (match != null) {
                ValueSetConceptSearchCriteriaDto valueSetConceptSearchCriteriaDto = new ValueSetConceptSearchCriteriaDto();
                valueSetConceptSearchCriteriaDto.setSearchText(match);
                valueSetConceptSearchCriteriaDto.setSearchType(1);
                valueSetConceptSearchCriteriaDto.setVersionOption(3);
                valueSetConceptSearchCriteriaDto.setFilterByValueSets(true);
                valueSetConceptSearchCriteriaDto.setValueSetOids(Arrays.asList(id));
                valueSetConceptSearchCriteriaDto.setConceptCodeSearch(true);
                ValueSetConceptResultDto valueSetConceptResultDto = this.service.findValueSetConcepts(valueSetConceptSearchCriteriaDto, 1, Integer.MAX_VALUE);
                valueSetConcepts = valueSetConceptResultDto.getValueSetConcepts();
            } else {
                valueSetConcepts = this.service
                        .getValueSetConceptsByValueSetVersionId(valuesetVersion.getId(), 1, Integer.MAX_VALUE)
                        .getValueSetConcepts();
            }

            // Get code systems and save all codes
            Set<String> codeSystemOids = new HashSet<>();
            Map<String, CodeSystem> uniqueIdCodeSystemMap = new HashMap<>();
            List<Code> codes = new ArrayList<Code>();
            for (ValueSetConcept pcode : valueSetConcepts) {
                if (pcode.getValueSetVersionId().equals(valuesetVersion.getId())) {
                    if (uniqueIdCodeSystemMap.get(pcode.getCodeSystemOid()) == null) {
                        CodeSystem cs = getCodeSystem(pcode.getCodeSystemOid());
                        uniqueIdCodeSystemMap.put(pcode.getCodeSystemOid(), cs);
                    }
                    Code code = new Code();
                    code.setValue(pcode.getConceptCode());
                    code.setDescription(pcode.getCodeSystemConceptName());
                    code.setComments(pcode.getDefinitionText());
//                    code.setUsage("R");
                    code.setCodeSystem(uniqueIdCodeSystemMap.get(pcode.getCodeSystemOid()).getHl70396Identifier());
                    codes.add(code);
                }

            }
            return codes;
        } catch (Exception e) {
            log.warn("PHINVADS call failed for getCodes({}, {}), trying fallback: {}", id, version, e.getMessage());
            return getCodesFromFallback(id, version, match);
        }
    }


    private List<Code> getCodesFromFallback(String id, String version, String match) throws IOException {
        if (fallbackService != null && fallbackService.hasFallbackData(id, version)) {
            log.info("Serving codes from local fallback files for {} v{}", id, version);
            return fallbackService.readCodes(id, version, match);
        }
        log.error("No fallback data available for {} v{}", id, version);
        return new ArrayList<>();
    }

    public CodesetMetadataResponse getCodesetMetadata(String id) throws NotFoundException, IOException {
        ValueSet valueset = getValueset(id);
        if(valueset == null){
            throw new IOException("Error while retrieving data from Phinvads web service");
        }
        List<ValueSetVersion> valuesetVersions = getValuesetVersions(id);
        CodesetMetadataResponse codesetMetadataResponse = new CodesetMetadataResponse();
        codesetMetadataResponse.setId(id);
        codesetMetadataResponse.setName(valueset.getCode());
        List<VersionMetadata> versions = new ArrayList<VersionMetadata>();
        VersionMetadata latestVersion = new VersionMetadata();
        for (ValueSetVersion v : valuesetVersions) {
                VersionMetadata versionMetadata = new VersionMetadata(String.valueOf(v.getVersionNumber()), v.getStatusDate());
                versions.add(versionMetadata);
                if (latestVersion.getVersion() == null || Integer.parseInt(latestVersion.getVersion()) < v.getVersionNumber()) {
                    latestVersion = versionMetadata;
                }
        }
        codesetMetadataResponse.setVersions(versions);
        codesetMetadataResponse.setLatestStableVersion(latestVersion);
        return codesetMetadataResponse;
    }

    @Override
    public CodesetVersionMetadataResponse getCodesetVersionMetadata(String id, String version) throws NotFoundException {
        ValueSet valueset = getValueset(id);
        if(valueset == null){
            throw new NotFoundException("Error while retrieving code set data from Phinvads web service");
        }
        ValueSetVersion valuesetVersion = getValuesetVersion(id, version);
        if(valueset == null){
            throw new NotFoundException("Error while retrieving code set version from Phinvads web service");
        }
        List<ValueSetConcept> valueSetConcepts = this.service
                .getValueSetConceptsByValueSetVersionId(valuesetVersion.getId(), 1, Integer.MAX_VALUE)
                .getValueSetConcepts();

        CodesetVersionMetadataResponse codesetMetadataResponse = new CodesetVersionMetadataResponse();
        codesetMetadataResponse.setId(id);
        codesetMetadataResponse.setName(valueset.getCode());
        codesetMetadataResponse.setDate(valuesetVersion.getStatusDate());
        codesetMetadataResponse.setVersion(String.valueOf(valuesetVersion.getVersionNumber()));
        codesetMetadataResponse.setNumberOfCodes(valueSetConcepts.size());

        return codesetMetadataResponse;
    }

}
