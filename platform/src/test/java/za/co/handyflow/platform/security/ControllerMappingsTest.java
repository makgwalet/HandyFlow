package za.co.handyflow.platform.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two controller methods on the same HTTP method and URL stop the whole application from starting ("Ambiguous mapping").
 * This reads the security controllers' source and fails with both names, so it shows up in the test run instead of at startup.
 * Plain @GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping with a string path only (that is all this module uses).
 */
class ControllerMappingsTest {

    private static final Path API = Path.of("src/main/java/za/co/handyflow/platform/security/api");
    private static final Pattern BASE = Pattern.compile("@RequestMapping\\(\\s*(?:(?:value|path)\\s*=\\s*)?\"([^\"]*)\"");
    private static final Pattern METHOD = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping(?:\\(\\s*(?:(?:value|path)\\s*=\\s*)?\"([^\"]*)\"[^)]*\\)|\\(\\s*\\)|(?![\\w(]))");

    static String norm(String p) {
        return p.replaceAll("/+", "/").replaceAll("\\{[^}/]+}", "{}").replaceAll("/$", "");
    }

    static Map<String, List<String>> mappings(Path dir) throws IOException {
        Map<String, List<String>> out = new TreeMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(x -> x.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(f);
                if (!src.contains("@RestController") && !src.contains("@Controller")) continue;
                Matcher b = BASE.matcher(src);
                String base = b.find() ? b.group(1) : "";
                Matcher m = METHOD.matcher(src);
                while (m.find()) {
                    String key = m.group(1).toUpperCase() + " " + norm(base + "/" + (m.group(2) == null ? "" : m.group(2)));
                    out.computeIfAbsent(key, k -> new ArrayList<>()).add(f.getFileName().toString());
                }
            }
        }
        return out;
    }

    @Test void noTwoSecurityEndpointsShareAMethodAndUrl() throws IOException {
        Map<String, List<String>> clashes = new TreeMap<>();
        mappings(API).forEach((k, v) -> { if (v.size() > 1) clashes.put(k, v); });
        assertTrue(clashes.isEmpty(), "Ambiguous mappings would stop startup: " + clashes);
    }

    @Test void theScanSeesTheControllersItShouldCheck() throws IOException {
        Map<String, List<String>> all = mappings(API);
        assertTrue(all.size() > 100, "expected well over 100 endpoints, saw " + all.size());
        assertTrue(all.containsKey("GET /api/v1/security/sites/{}/posts"), "post orders endpoint");
        assertTrue(all.containsKey("GET /api/v1/security/sites/{}/coverage-posts"), "coverage posts endpoint");
    }

    @Test void theMatcherDetectsADuplicate() throws IOException {
        Path tmp = Files.createTempDirectory("ctl");
        Files.writeString(tmp.resolve("A.java"), "@RestController @RequestMapping(\"/api/x\") class A { @GetMapping(\"/s/{id}/p\") void a(){} }");
        Files.writeString(tmp.resolve("B.java"), "@RestController class B { @GetMapping(\"/api/x/s/{siteId}/p\") void b(){} @PostMapping(\"/api/x/s/{siteId}/p\") void c(){} }");
        Map<String, List<String>> m = mappings(tmp);
        assertTrue(m.get("GET /api/x/s/{}/p").size() == 2);
        assertTrue(m.get("POST /api/x/s/{}/p").size() == 1);
    }
}
