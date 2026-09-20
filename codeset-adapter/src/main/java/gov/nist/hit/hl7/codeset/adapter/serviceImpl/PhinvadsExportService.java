package gov.nist.hit.hl7.codeset.adapter.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.hit.hl7.codeset.adapter.model.Code;
import gov.nist.hit.hl7.codeset.adapter.model.Codeset;
import gov.nist.hit.hl7.codeset.adapter.model.CodesetVersion;
import gov.nist.hit.hl7.codeset.adapter.model.VersionMetadata;
import gov.nist.hit.hl7.codeset.adapter.repository.CodesetRepository;
import gov.nist.hit.hl7.codeset.adapter.repository.CodesetVersionRepository;
import gov.cdc.vocab.service.VocabService;
import gov.cdc.vocab.service.bean.CodeSystem;
import gov.cdc.vocab.service.bean.ValueSetConcept;
import gov.cdc.vocab.service.bean.ValueSetVersion;
import gov.cdc.vocab.service.dto.input.CodeSystemSearchCriteriaDto;
import gov.cdc.vocab.service.dto.input.ValueSetVersionSearchCriteriaDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

@Service
public class PhinvadsExportService {

    private static final Logger log = LoggerFactory.getLogger(PhinvadsExportService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MongoOperations mongoOps;
    private final CodesetRepository codesetRepository;
    private final CodesetVersionRepository codesetVersionRepository;
    private final PhinvadsServiceImpl phinvadsService;

    @Value("${phinvads.export.dir:./data/phinvads-export}")
    private String exportDir;

    @Value("${phinvads.export.delay-between-calls-ms:3000}")
    private long delayBetweenCallsMs;

    @Value("${phinvads.export.delay-between-codesets-ms:5000}")
    private long delayBetweenCodesetsMs;

    @Value("${phinvads.export.page-size:5000}")
    private int pageSize;

    @Value("${phinvads.export.max-retries:3}")
    private int maxRetries;

    @Value("${phinvads.export.retry-delay-ms:30000}")
    private long retryDelayMs;

    @Value("${phinvads.export.all-versions:false}")
    private boolean exportAllVersionsDefault;

    @Value("${phinvads.export.max-codesets:0}")
    private int maxCodesetsDefault;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicInteger exportedCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final java.util.concurrent.ConcurrentLinkedQueue<Map<String, String>> failures =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private int totalVersions = 0;
    private String currentCodeset = "";
    private String currentVersion = "";
    private Instant startTime;


    public PhinvadsExportService(MongoOperations mongoOps,
                                 CodesetRepository codesetRepository,
                                 CodesetVersionRepository codesetVersionRepository,
                                 PhinvadsServiceImpl phinvadsService) {
        this.mongoOps = mongoOps;
        this.codesetRepository = codesetRepository;
        this.codesetVersionRepository = codesetVersionRepository;
        this.phinvadsService = phinvadsService;
    }


    public Map<String, Object> runFullExport() {
        return runFullExport(exportAllVersionsDefault, maxCodesetsDefault);
    }


    public Map<String, Object> runFullExport(boolean allVersions, int maxCodesets) {
        if (running.getAndSet(true)) {
            return Map.of("status", "ALREADY_RUNNING", "message", "Export is already in progress. Check /status.");
        }

        stopRequested.set(false);
        exportedCount.set(0);
        skippedCount.set(0);
        failedCount.set(0);
        failures.clear();
        startTime = Instant.now();

        try {
            Path exportPath = Path.of(exportDir);
            Files.createDirectories(exportPath);

            List<Codeset> codesets = codesetRepository.findAll();
            if (maxCodesets > 0 && codesets.size() > maxCodesets) {
                codesets = codesets.subList(0, maxCodesets);
            }
            log.info("=== PHINVADS EXPORT START === {} codesets to process (allVersions={}, maxCodesets={})",
                    codesets.size(), allVersions, maxCodesets);

            totalVersions = codesets.stream()
                    .mapToInt(c -> {
                        if (c.getVersions() == null || c.getVersions().isEmpty()) return 0;
                        return allVersions ? c.getVersions().size() : 1;
                    })
                    .sum();
            log.info("Total versions to attempt: {}", totalVersions);

            for (Codeset codeset : codesets) {
                if (stopRequested.get()) {
                    log.info("=== EXPORT STOPPED BY USER ===");
                    break;
                }

                currentCodeset = codeset.getIdentifier();
                List<VersionMetadata> versions = codeset.getVersions();
                if (versions == null || versions.isEmpty()) {
                    log.info("Skipping {} — no versions", codeset.getIdentifier());
                    continue;
                }

                if (allVersions) {
                    for (VersionMetadata v : versions) {
                        if (stopRequested.get()) break;
                        exportSingleVersion(codeset, v.getVersion());
                        if (!stopRequested.get()) {
                            throttle(delayBetweenCallsMs);
                        }
                    }
                } else {
                    VersionMetadata latestVersion = codeset.getLatestVersion();
                    if (latestVersion == null) {
                        latestVersion = versions.get(versions.size() - 1);
                    }
                    exportSingleVersion(codeset, latestVersion.getVersion());
                }

                if (!stopRequested.get()) {
                    throttle(delayBetweenCodesetsMs);
                }
            }

            saveProgressFile();
            log.info("=== PHINVADS EXPORT COMPLETE === Exported: {}, Skipped: {}, Failed: {}",
                    exportedCount.get(), skippedCount.get(), failedCount.get());

        } catch (Exception e) {
            log.error("Export failed with exception: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }

        return getStatus();
    }

    private void exportSingleVersion(Codeset codeset, String version) {
        String oid = codeset.getIdentifier();
        currentVersion = version;

        if (oid == null || version == null) {
            log.warn("Skipping codeset with null identifier or version (codesetId={}, version={})",
                    codeset.getId(), version);
            recordFailure(String.valueOf(oid), String.valueOf(version), "null identifier or version");
            return;
        }

        Path file = Path.of(exportDir, oid, version + ".json.gz");
        if (Files.exists(file)) {
            try {
                long fileSize = Files.size(file);
                if (fileSize > 20) { // Not just an empty/corrupt gzip
                    log.info("SKIP {} v{} — already exported ({} bytes)", oid, version, fileSize);
                    skippedCount.incrementAndGet();
                    return;
                }
            } catch (IOException ignored) {}
        }

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Exporting {} v{} (attempt {}/{})", oid, version, attempt, maxRetries);

                ValueSetVersion vsVersion = phinvadsService.getValuesetVersion(oid, version);
                if (vsVersion == null) {
                    log.warn("Could not find PHINVADS version for {} v{}", oid, version);
                    recordFailure(oid, version, "PHINVADS version not found");
                    return;
                }

                throttle(delayBetweenCallsMs);

                List<Map<String, String>> allCodes = new ArrayList<>();
                int page = 1;
                boolean hasMore = true;

                while (hasMore && !stopRequested.get()) {
                    log.info("  Fetching page {} (pageSize={}) for {} v{}", page, pageSize, oid, version);

                    List<ValueSetConcept> concepts = phinvadsService.getService()
                            .getValueSetConceptsByValueSetVersionId(vsVersion.getId(), page, pageSize)
                            .getValueSetConcepts();

                    if (concepts == null || concepts.isEmpty()) {
                        hasMore = false;
                    } else {
                        for (ValueSetConcept c : concepts) {
                            Map<String, String> code = new LinkedHashMap<>();
                            code.put("value", c.getConceptCode());
                            code.put("description", c.getCodeSystemConceptName());
                            code.put("comments", c.getDefinitionText());
                            code.put("codeSystemOid", c.getCodeSystemOid());
                            allCodes.add(code);
                        }

                        if (concepts.size() < pageSize) {
                            hasMore = false;
                        } else {
                            page++;
                            throttle(delayBetweenCallsMs);
                        }
                    }
                }

                if (stopRequested.get()) return;

                Set<String> uniqueOids = new HashSet<>();
                for (Map<String, String> code : allCodes) {
                    String csOid = code.get("codeSystemOid");
                    if (csOid != null) uniqueOids.add(csOid);
                }

                Map<String, String> codeSystemMap = new HashMap<>();
                for (String csOid : uniqueOids) {
                    if (stopRequested.get()) return;
                    try {
                        CodeSystem cs = phinvadsService.getCodeSystem(csOid);
                        codeSystemMap.put(csOid, cs.getHl70396Identifier());
                        throttle(delayBetweenCallsMs);
                    } catch (Exception e) {
                        log.warn("  Could not resolve code system {}: {}", csOid, e.getMessage());
                        codeSystemMap.put(csOid, csOid); // fallback to raw OID
                    }
                }

                for (Map<String, String> code : allCodes) {
                    String csOid = code.remove("codeSystemOid");
                    code.put("codeSystem", codeSystemMap.getOrDefault(csOid, csOid));
                }

                Path dir = Path.of(exportDir, oid);
                Files.createDirectories(dir);

                Path tmpFile = dir.resolve(version + ".json.gz.tmp");
                try (OutputStream os = new GZIPOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(tmpFile)))) {
                    objectMapper.writeValue(os, allCodes);
                }
                Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);

                long fileSize = Files.size(file);
                log.info("EXPORTED {} v{} — {} codes, {} bytes compressed",
                        oid, version, allCodes.size(), fileSize);
                exportedCount.incrementAndGet();
                return;

            } catch (Exception e) {
                log.error("Attempt {}/{} failed for {} v{}: {}",
                        attempt, maxRetries, oid, version, e.getMessage());

                if (attempt < maxRetries) {
                    log.info("  Waiting {}ms before retry...", retryDelayMs);
                    throttle(retryDelayMs);
                } else {
                    log.error("  All retries exhausted for {} v{}", oid, version);
                    recordFailure(oid, version, e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
    }

    public Map<String, Object> requestStop() {
        if (!running.get()) {
            return Map.of("status", "NOT_RUNNING", "message", "No export is running.");
        }
        stopRequested.set(true);
        return Map.of("status", "STOP_REQUESTED", "message", "Export will stop after current version completes.");
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", running.get());
        status.put("exported", exportedCount.get());
        status.put("skipped", skippedCount.get());
        status.put("failed", failedCount.get());
        status.put("failures", new ArrayList<>(failures));
        status.put("totalVersions", totalVersions);
        status.put("currentCodeset", currentCodeset);
        status.put("currentVersion", currentVersion);
        status.put("exportDir", exportDir);

        if (startTime != null) {
            long elapsed = Instant.now().getEpochSecond() - startTime.getEpochSecond();
            status.put("elapsedSeconds", elapsed);
            status.put("elapsedHuman", formatDuration(elapsed));
        }

        try {
            long filesOnDisk = countExportedFiles();
            status.put("filesOnDisk", filesOnDisk);
        } catch (IOException e) {
            status.put("filesOnDisk", "error: " + e.getMessage());
        }

        status.put("config", Map.of(
                "delayBetweenCallsMs", delayBetweenCallsMs,
                "delayBetweenCodesetsMs", delayBetweenCodesetsMs,
                "pageSize", pageSize,
                "maxRetries", maxRetries,
                "retryDelayMs", retryDelayMs
        ));

        return status;
    }

    private void recordFailure(String oid, String version, String reason) {
        failedCount.incrementAndGet();
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("oid", oid);
        entry.put("version", version);
        entry.put("reason", reason != null ? reason : "unknown");
        entry.put("when", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        failures.add(entry);
    }

    private void throttle(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long countExportedFiles() throws IOException {
        Path exportPath = Path.of(exportDir);
        if (!Files.exists(exportPath)) return 0;
        try (var walk = Files.walk(exportPath)) {
            return walk.filter(p -> p.toString().endsWith(".json.gz")).count();
        }
    }

    private void saveProgressFile() {
        try {
            Path progressFile = Path.of(exportDir, "export-progress.json");
            Map<String, Object> progress = new LinkedHashMap<>();
            progress.put("lastRun", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            progress.put("exported", exportedCount.get());
            progress.put("skipped", skippedCount.get());
            progress.put("failed", failedCount.get());
            progress.put("failures", new ArrayList<>(failures));
            progress.put("totalVersions", totalVersions);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(progressFile.toFile(), progress);
        } catch (IOException e) {
            log.error("Could not save progress file: {}", e.getMessage());
        }
    }

    private String formatDuration(long seconds) {
        long hrs = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%dh %dm %ds", hrs, mins, secs);
    }
}
