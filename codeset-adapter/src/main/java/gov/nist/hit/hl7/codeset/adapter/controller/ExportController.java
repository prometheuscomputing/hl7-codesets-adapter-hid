package gov.nist.hit.hl7.codeset.adapter.controller;

import gov.nist.hit.hl7.codeset.adapter.serviceImpl.PhinvadsExportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private final PhinvadsExportService exportService;

    public ExportController(PhinvadsExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startExport(
            @RequestParam(required = false) Boolean allVersions,
            @RequestParam(required = false) Integer maxCodesets) {

        boolean allVer = allVersions != null ? allVersions : false;
        int maxCs = maxCodesets != null ? maxCodesets : 0;
        boolean useDefaults = allVersions == null && maxCodesets == null;

        // ForkJoinPool common-pool threads don't inherit the fat-jar classloader;
        // Hessian then resolves gov.cdc DTOs as HashMap. Pin the TCCL for the task.
        ClassLoader appClassLoader = getClass().getClassLoader();
        CompletableFuture.runAsync(() -> {
            Thread.currentThread().setContextClassLoader(appClassLoader);
            if (useDefaults) {
                exportService.runFullExport();
            } else {
                exportService.runFullExport(allVer, maxCs);
            }
        });

        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        return ResponseEntity.ok(Map.of(
                "status", "STARTED",
                "message", "Export started in background. Use GET /api/v1/export/status to monitor progress.",
                "tip", "The export is resumable. Already-exported versions will be skipped.",
                "allVersions", useDefaults ? "(from application.properties)" : String.valueOf(allVer),
                "maxCodesets", useDefaults ? "(from application.properties)" : String.valueOf(maxCs)
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(exportService.getStatus());
    }

    @GetMapping("/failures")
    public ResponseEntity<Object> getFailures() {
        Map<String, Object> status = exportService.getStatus();
        return ResponseEntity.ok(Map.of(
                "count", status.getOrDefault("failed", 0),
                "failures", status.getOrDefault("failures", java.util.Collections.emptyList())
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopExport() {
        return ResponseEntity.ok(exportService.requestStop());
    }
}
