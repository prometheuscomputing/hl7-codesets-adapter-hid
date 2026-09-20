package gov.nist.hit.hl7.codeset.adapter.serviceImpl;

import gov.nist.hit.hl7.codeset.adapter.exception.NotFoundException;
import gov.nist.hit.hl7.codeset.adapter.model.Code;
import gov.nist.hit.hl7.codeset.adapter.model.Codeset;
import gov.nist.hit.hl7.codeset.adapter.model.CodesetVersion;
import gov.nist.hit.hl7.codeset.adapter.model.response.*;
import gov.nist.hit.hl7.codeset.adapter.repository.CodesetRepository;
import gov.nist.hit.hl7.codeset.adapter.repository.CodesetVersionRepository;
import gov.nist.hit.hl7.codeset.adapter.model.request.CodesetSearchCriteria;
import gov.nist.hit.hl7.codeset.adapter.service.CodeIndexCache;
import gov.nist.hit.hl7.codeset.adapter.service.CodesetService;
import gov.nist.hit.hl7.codeset.adapter.service.ProviderService;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CodesetServiceImpl implements CodesetService {
    private final CodesetRepository codesetRepository;
    private final CodesetVersionRepository codesetVersionRepository;
    private final MongoTemplate mongoTemplate;
    private final List<ProviderService> providerServices;
    private final CodeIndexCache codeIndex;

    public CodesetServiceImpl(CodesetRepository codesetRepository, CodesetVersionRepository codesetVersionRepository,
                              MongoTemplate mongoTemplate, List<ProviderService> providerServices, CodeIndexCache codeIndex) {
        this.codesetRepository = codesetRepository;
        this.codesetVersionRepository = codesetVersionRepository;
        this.mongoTemplate = mongoTemplate;
        this.providerServices = providerServices;
        this.codeIndex = codeIndex;
    }


    private ProviderService provider(String name) {
        return providerServices.stream()
                .filter(p -> p.getProvider().getName().equals(name.toLowerCase()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider " + name.toLowerCase() + " not found"));
    }

    @Override
    public List<ProvidersResponse> getProviders() throws IOException {
        List<ProvidersResponse> providers = new ArrayList<ProvidersResponse>();
        providerServices.stream().forEach(p -> {
            providers.add(new ProvidersResponse(p.getProvider().getName(), p.getProvider().getLabel()));

        });
        return providers;
    }

    @Override
    public List<CodesetMetadataResponse> getCodesets(String provider, CodesetSearchCriteria criteria) throws IOException {
        MatchOperation matchOperation = Aggregation.match(Criteria.where("provider").regex("^" + Pattern.quote(provider) + "$", "i")
        );

        ProjectionOperation projectionOperation = Aggregation.project()
                .and("name").as("name")
                .and("versions").as("versions")
                .and("latestVersion").as("latestStableVersion");

        projectionOperation = projectionOperation.and("identifier").as("identifier");


        // Execute the aggregation
        Aggregation aggregation = Aggregation.newAggregation(matchOperation, projectionOperation);
        AggregationResults<CodesetMetadataResponse> results = mongoTemplate.aggregate(aggregation, "codeset", CodesetMetadataResponse.class);
        return results.getMappedResults();
    }

    @Override
    public CodesetMetadataResponse getCodesetMetadata(String provider, String id) throws IOException, NotFoundException {
        ProviderService providerService = provider(provider);
        try {
            CodesetMetadataResponse codesetMetadataResponse = providerService.getCodesetMetadata(id);
            return codesetMetadataResponse;

        } catch (Exception e) {
            MatchOperation matchOperation = Aggregation.match(Criteria.where("provider").regex("^" + Pattern.quote(provider) + "$", "i").and("identifier").is(id)
            );

            ProjectionOperation projectionOperation = Aggregation.project()
                    .and("name").as("name")
                    .and("versions").as("versions")
                    .and("latestVersion").as("latestStableVersion");

            projectionOperation = projectionOperation.and("identifier").as("identifier");


            // Execute the aggregation
            Aggregation aggregation = Aggregation.newAggregation(matchOperation, projectionOperation);
            AggregationResults<CodesetMetadataResponse> results = mongoTemplate.aggregate(aggregation, "codeset", CodesetMetadataResponse.class);
            CodesetMetadataResponse fallback = results.getUniqueMappedResult();
            if (fallback == null) {
                throw new NotFoundException("Codeset " + id + " not found for provider " + provider);
            }
            return fallback;
        }

    }
//    public List<Codeset> getCodesets(CodesetSearchCriteria criteria) throws IOException {
//        return codesetRepository.findAll((Specification<Codeset>) (root, query, criteriaBuilder) -> {
//            List<Predicate> predicates = new ArrayList<>();
//
//            if (criteria.getScope() != null) {
//                predicates.add(criteriaBuilder.equal(root.get("scope"), criteria.getScope()));
//            }
//            if (criteria.getVersion() != null) {
//                predicates.add(criteriaBuilder.equal(root.get("versionNumber"), criteria.getVersion()));
//            }
//            if (criteria.getName() != null) {
//                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + criteria.getName().toLowerCase() + "%"));
//            }
//
//            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
//        });
//    }

    @Override
    public CodesetVersionMetadataResponse getCodesetVersionMetadata(String provider, String id, String version) throws IOException, NotFoundException {
        ProviderService providerService = provider(provider);
        return providerService.getCodesetVersionMetadata(id, version);

    }

    public CodesetResponse getCodeset(String provider, String id, CodesetSearchCriteria searchCriteria) throws IOException, NotFoundException {
        ProviderService providerService = provider(provider);


        String version = searchCriteria.getVersion();
        if (version == null) {
            version = providerService.getLatestVersion(id);
        }
        if(version == null) {
            throw new IOException("Error while retrieving latest version from Phinvads web service");
        }

        providerService.getCodesetAndSave(id, version);

        // Step 1: Initial match criteria for the Codeset based on provider and ID
        Criteria criteria = Criteria.where("provider").regex("^" + Pattern.quote(provider) + "$", "i")
                .and("identifier").is(id);

        List<AggregationOperation> operations = new ArrayList<>();
        operations.add(Aggregation.match(criteria));

        // Step 2: Lookup for CodesetVersion
        LookupOperation lookupCodesetVersion = LookupOperation.newLookup()
                .from("codesetVersion")
                .localField("codeSetVersions.$id")
                .foreignField("_id")
                .as("codesetVersion");
        operations.add(lookupCodesetVersion);

        // Step 3: Unwind CodesetVersion
        UnwindOperation unwindCodesetVersion = Aggregation.unwind("codesetVersion");
        operations.add(unwindCodesetVersion);

        // Step 4: Match on the version using $expr
        MatchOperation matchVersion;

        matchVersion = Aggregation.match(Criteria.where("codesetVersion.version").is(version));

        operations.add(matchVersion);


        // Step 6: Use projection
        ProjectionOperation projection;

        projection = Aggregation.project()
                .and("name").as("name")
                .and("latestVersion").as("latestStableVersion")
                .and("identifier").as("identifier")
                .and("codesetVersion.version").as("version.version")
                .and("codesetVersion._id").as("version._id")
                .and("codesetVersion.dateUpdated").as("version.date");
        operations.add(projection);

        // Final Aggregation
        Aggregation finalAggregation = Aggregation.newAggregation(operations);
        AggregationResults<CodesetResponse> finalResults = mongoTemplate.aggregate(finalAggregation, "codeset", CodesetResponse.class);


        CodesetResponse codesetResponse = finalResults.getUniqueMappedResult();
        if (codesetResponse == null) {
            throw new NotFoundException("Codeset not found");
        }

        Codeset codeset = codesetRepository.findByIdentifier(id).orElse(null);
        CodesetVersion codesetVersion = codesetVersionRepository.findByCodesetIdAndVersion(codeset.getId(), version).orElse(null);
        String versionId = codesetResponse.getVersion().getId();
        boolean storedLocally = !CodesetVersion.CodesStatus.NOT_NEEDED.equals(codesetVersion.getCodesStatus());
        String match = searchCriteria.getMatch();
        // `version` is reassigned above; the loader lambda needs a final copy.
        String resolvedVersion = version;

        // Whole-set responses and point lookups are served from one in-memory
        // copy of the set, fetched once per version and kept for the cache
        // TTL. The key carries the resolved version number (not "latest") so
        // a request without a version and one that names the current version
        // share the same entry. A code that is not in the set is simply not
        // in it; no further search is made.
        String key = provider.toLowerCase() + "|" + id + "|" + resolvedVersion;
        Supplier<List<Code>> loader = () -> {
            try {
                return wholeSet(providerService, id, resolvedVersion, versionId, storedLocally);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        List<Code> codes;
        try {
            codes = match == null ? codeIndex.all(key, loader) : codeIndex.lookup(key, match, loader);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        List<CodeResponse> codeResponses = codes.stream()
                .map(code -> new CodeResponse(code))
                .collect(Collectors.toList());
        codesetResponse.setCodes(codeResponses);

        // Add codeMatchValue only if searchCriteria.getMatch() is provided
        if (searchCriteria.getMatch() != null) {
            codesetResponse.setCodeMatchValue(searchCriteria.getMatch());
        }
        return codesetResponse;
    }

    /**
     * Every code of one code set version: from the local collection when the
     * import stored them, otherwise from the provider (sets over 500 codes are
     * never stored locally). An empty local result falls through to the
     * provider, as it always has.
     */
    private List<Code> wholeSet(ProviderService providerService, String id, String version, String versionId, boolean storedLocally) throws IOException {
        if (!storedLocally) {
            return providerService.getCodes(id, version, null);
        }
        List<Code> codes = mongoTemplate.find(Query.query(Criteria.where("codesetversionId").is(versionId)), Code.class);
        if (codes.isEmpty()) {
            codes = providerService.getCodes(id, version, null);
        }
        return codes;
    }

}
