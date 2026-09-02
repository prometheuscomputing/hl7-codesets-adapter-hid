package gov.nist.hit.hl7.codeset.adapter.service;

import gov.nist.hit.hl7.codeset.adapter.controller.CodesetController;
import gov.nist.hit.hl7.codeset.adapter.model.request.CodesetSearchCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads the code sets the validators bind to as soon as the service is up,
 * so the first validation after a restart does not pay for the whole-set
 * fetch. One whole-set request per set fills both the serialized response
 * cache and the lookup index. Runs on its own thread; a set that fails to
 * load is logged and the next one is tried, and the path used is the one a
 * real request takes, so nothing is warmed that a request would not build.
 */
@Component
public class CodeIndexWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CodeIndexWarmup.class);
    private static final String PROVIDER = "phinvads";

    private final CodesetController controller;
    private final List<Target> targets;

    public CodeIndexWarmup(CodesetController controller, @Value("${codeset.warmup.sets:}") String sets) {
        this.controller = controller;
        this.targets = parse(sets);
    }

    record Target(String oid, String version) {
    }

    /** "oid" or "oid:version", comma separated; blanks ignored. */
    static List<Target> parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return Collections.emptyList();
        }
        List<Target> targets = new ArrayList<>();
        for (String item : spec.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                targets.add(new Target(trimmed, null));
            } else {
                targets.add(new Target(trimmed.substring(0, colon).trim(), trimmed.substring(colon + 1).trim()));
            }
        }
        return targets;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (targets.isEmpty()) {
            return;
        }
        Thread thread = new Thread(this::warmAll, "codeset-warmup");
        thread.setDaemon(true);
        thread.start();
    }

    void warmAll() {
        for (Target target : targets) {
            long started = System.currentTimeMillis();
            String shown = target.version() == null ? "latest" : target.version();
            try {
                CodesetSearchCriteria wholeSet = new CodesetSearchCriteria();
                wholeSet.setVersion(target.version());
                controller.getCodeset(PROVIDER, target.oid(), wholeSet);
                log.info("Warmed {} v{} in {} ms", target.oid(), shown, System.currentTimeMillis() - started);
            } catch (Exception e) {
                log.warn("Warm-up of {} v{} failed: {}", target.oid(), shown, e.toString());
            }
        }
    }
}
