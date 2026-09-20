package gov.nist.hit.hl7.codeset.adapter.serviceImpl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.hit.hl7.codeset.adapter.model.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;


@Service
public class PhinvadsFallbackService {

    private static final Logger log = LoggerFactory.getLogger(PhinvadsFallbackService.class);
    private static final String CLASSPATH_BASE = "data/phinvads-export";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @Value("${phinvads.export.dir:./data/phinvads-export}")
    private String exportDir;


    public boolean hasFallbackData(String oid, String version) {
        Path file = Path.of(exportDir, oid, version + ".json.gz");
        if (Files.exists(file) && isValidFile(file)) {
            return true;
        }
        return classpathResource(oid, version).exists();
    }


    public String findLatestExportedVersion(String oid) {
        Path codesetDir = Path.of(exportDir, oid);
        if (Files.exists(codesetDir)) {
            try (var files = Files.list(codesetDir)) {
                String latest = files
                        .filter(p -> p.toString().endsWith(".json.gz"))
                        .map(p -> p.getFileName().toString().replace(".json.gz", ""))
                        .filter(PhinvadsFallbackService::isInteger)
                        .max(Comparator.comparingInt(Integer::parseInt))
                        .orElse(null);
                if (latest != null) {
                    return latest;
                }
            } catch (IOException e) {
                log.error("Error scanning export directory for {}: {}", oid, e.getMessage());
            }
        }

        // Fall back to the data bundled inside the jar
        try {
            Resource[] resources = resolver.getResources("classpath*:" + CLASSPATH_BASE + "/" + oid + "/*.json.gz");
            return Arrays.stream(resources)
                    .map(Resource::getFilename)
                    .filter(Objects::nonNull)
                    .map(name -> name.replace(".json.gz", ""))
                    .filter(PhinvadsFallbackService::isInteger)
                    .max(Comparator.comparingInt(Integer::parseInt))
                    .orElse(null);
        } catch (IOException e) {
            log.error("Error scanning classpath export data for {}: {}", oid, e.getMessage());
            return null;
        }
    }

    public List<Code> readCodes(String oid, String version, String match) throws IOException {
        Path file = Path.of(exportDir, oid, version + ".json.gz");

        if (Files.exists(file)) {
            log.info("Reading fallback data from file: {} (match={})", file, match);
            try (InputStream fis = Files.newInputStream(file)) {
                return parseCodes(fis, file.toString(), match);
            }
        }

        Resource resource = classpathResource(oid, version);
        if (resource.exists()) {
            log.info("Reading fallback data from classpath: {} (match={})", resource.getDescription(), match);
            try (InputStream is = resource.getInputStream()) {
                return parseCodes(is, resource.getDescription(), match);
            }
        }

        log.warn("No fallback data found for {} v{} (checked {} and classpath)", oid, version, file);
        return Collections.emptyList();
    }


    public List<String> listExportedCodesets() {
        Set<String> oids = new TreeSet<>();

        Path dir = Path.of(exportDir);
        if (Files.exists(dir)) {
            try (var dirs = Files.list(dir)) {
                dirs.filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .filter(name -> name.contains("."))
                        .forEach(oids::add);
            } catch (IOException e) {
                log.error("Error listing exported codesets: {}", e.getMessage());
            }
        }

        try {
            Resource[] resources = resolver.getResources("classpath*:" + CLASSPATH_BASE + "/*/*.json.gz");
            for (Resource resource : resources) {
                String oid = extractOid(resource);
                if (oid != null) {
                    oids.add(oid);
                }
            }
        } catch (IOException e) {
            log.error("Error listing bundled codesets: {}", e.getMessage());
        }

        return new ArrayList<>(oids);
    }

    private Resource classpathResource(String oid, String version) {
        return resolver.getResource("classpath:" + CLASSPATH_BASE + "/" + oid + "/" + version + ".json.gz");
    }


    private String extractOid(Resource resource) {
        try {
            String url = resource.getURL().toString();
            int base = url.lastIndexOf(CLASSPATH_BASE + "/");
            if (base < 0) return null;
            String relative = url.substring(base + CLASSPATH_BASE.length() + 1);
            int slash = relative.indexOf('/');
            return slash > 0 ? relative.substring(0, slash) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private List<Code> parseCodes(InputStream in, String source, String match) throws IOException {
        List<Code> results = new ArrayList<>();

        try (InputStream bis = new BufferedInputStream(in);
             InputStream gis = new GZIPInputStream(bis);
             JsonParser parser = objectMapper.getFactory().createParser(gis)) {

            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_ARRAY) {
                throw new IOException("Expected JSON array in " + source);
            }

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                Map<String, String> map = objectMapper.readValue(parser, Map.class);

                String value = map.get("value");

                if (match != null && !match.equals(value)) {
                    continue;
                }

                Code code = new Code();
                code.setValue(value);
                code.setDescription(map.get("description"));
                code.setComments(map.get("comments"));
                code.setCodeSystem(map.get("codeSystem"));
                results.add(code);
            }
        }

        log.info("Read {} codes from fallback source: {}", results.size(), source);
        return results;
    }

    private static boolean isInteger(String v) {
        try {
            Integer.parseInt(v);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidFile(Path file) {
        try {
            return Files.size(file) > 20;
        } catch (IOException e) {
            return false;
        }
    }
}
