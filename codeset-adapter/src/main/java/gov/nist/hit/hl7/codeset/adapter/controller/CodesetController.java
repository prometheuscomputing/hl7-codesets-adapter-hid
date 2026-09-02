package gov.nist.hit.hl7.codeset.adapter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gov.nist.hit.hl7.codeset.adapter.exception.NotFoundException;
import gov.nist.hit.hl7.codeset.adapter.model.request.CodesetSearchCriteria;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetMetadataResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetVersionMetadataResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.ProvidersResponse;
import gov.nist.hit.hl7.codeset.adapter.service.CodesetResponseCache;
import gov.nist.hit.hl7.codeset.adapter.serviceImpl.CodesetServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CodesetController {
    private static final Logger log = LoggerFactory.getLogger(CodesetController.class);
    private final CodesetServiceImpl codesetService;
    private final CodesetResponseCache responseCache;
    private final ObjectMapper objectMapper;

    public CodesetController(CodesetServiceImpl codesetService, CodesetResponseCache responseCache, ObjectMapper objectMapper) {
        this.codesetService = codesetService;
        this.responseCache = responseCache;
        // The MVC converter in this app writes dates as epoch millis while the
        // injected mapper writes ISO strings; the consumers parse the millis
        // form, so the cached bytes have to match it.
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    @GetMapping("/providers")
    public ResponseEntity<List<ProvidersResponse>> getProviders() throws IOException {
        List<ProvidersResponse> providers = codesetService.getProviders();
        return new ResponseEntity<>(providers, HttpStatus.OK);
    }

    @GetMapping("/{provider}/codesets")
    public ResponseEntity<List<CodesetMetadataResponse>> getCodesets(@PathVariable String provider, @ModelAttribute CodesetSearchCriteria criteria) throws IOException {
        List<CodesetMetadataResponse> codesets = codesetService.getCodesets(provider, criteria);
        return new ResponseEntity<>(codesets, HttpStatus.OK);
    }

    @GetMapping("/{provider}/codesets/{id}/metadata")
    public ResponseEntity<CodesetMetadataResponse> getCodesetMetadata(@PathVariable String provider,@PathVariable String id) throws IOException, NotFoundException {
        CodesetMetadataResponse codeset = codesetService.getCodesetMetadata(provider, id);
        return new ResponseEntity<>(codeset, HttpStatus.OK);
    }
    @GetMapping("/{provider}/codesets/{id}/versions/{version}/metadata")
    public ResponseEntity<CodesetVersionMetadataResponse> getCodesetMetadata(@PathVariable String provider,@PathVariable String id,@PathVariable String version) throws IOException, NotFoundException {
        CodesetVersionMetadataResponse codeset = codesetService.getCodesetVersionMetadata(provider, id, version);
        return new ResponseEntity<>(codeset, HttpStatus.OK);
    }

    @GetMapping("/{provider}/codesets/{id}")
    public ResponseEntity<byte[]> getCodeset(@PathVariable String provider,@PathVariable String id, @ModelAttribute CodesetSearchCriteria criteria) throws IOException, NotFoundException {
        // A match query is a point lookup and stays cheap; only whole-set
        // responses are worth keeping, and they are what the validators pull
        // on every message.
        boolean cacheable = criteria.getMatch() == null;
        String key = provider.toLowerCase() + "|" + id + "|" + (criteria.getVersion() == null ? "latest" : criteria.getVersion());
        long started = System.currentTimeMillis();
        if (cacheable) {
            byte[] cached = responseCache.get(key);
            if (cached != null) {
                log.debug("Whole set {} served from cache, {} bytes", key, cached.length);
                return json(cached);
            }
        }
        CodesetResponse codeset = codesetService.getCodeset(provider, id, criteria);
        byte[] body = objectMapper.writeValueAsBytes(codeset);
        if (cacheable) {
            // A set with no codes is almost certainly a failed fetch (the
            // provider answers an outage with an empty list); keeping it would
            // serve that failure for the whole TTL.
            boolean hasCodes = codeset.getCodes() != null && !codeset.getCodes().isEmpty();
            if (hasCodes) {
                responseCache.put(key, body);
            }
            log.info("Whole set {} built, {} bytes, {} ms{}", key, body.length, System.currentTimeMillis() - started,
                    hasCodes ? "" : " (empty, not cached)");
        } else {
            log.info("Lookup {} match={} hits={} {} ms", key, criteria.getMatch(),
                    codeset.getCodes() == null ? 0 : codeset.getCodes().size(), System.currentTimeMillis() - started);
        }
        return json(body);
    }

    private ResponseEntity<byte[]> json(byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

}
