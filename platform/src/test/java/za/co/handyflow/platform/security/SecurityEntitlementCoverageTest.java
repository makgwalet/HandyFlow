package za.co.handyflow.platform.security;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.billing.config.ModuleEntitlementPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module entitlement for Security is applied centrally (ModuleEntitlementInterceptorConfig), by URL prefix.
 * This test makes sure no security endpoint can slip outside that net: every mapping in the security controllers
 * must resolve to the "security" module, unless it is on the short list of public, pre-login paths.
 */
class SecurityEntitlementCoverageTest {

    /** Public or pre-login on purpose: no tenant is known when they are called. */
    static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/v1/auth/guard/",   // guard login and device activation
            "/api/v1/portal/");      // client portal, opened by a secret link

    private static final Pattern CLASS_MAP = Pattern.compile("(?m)^@RequestMapping\\(\\s*\"([^\"]*)\"");
    private static final Pattern METHOD_MAP = Pattern.compile(
            "(?m)^\\s*@(?:Get|Post|Put|Patch|Delete)Mapping(?:\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"[^)]*\\))?");

    static List<String> allPaths(Path dir) throws IOException {
        List<String> out = new java.util.ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith("Controller.java")).sorted().toList()) {
                String src = Files.readString(p);
                Matcher c = CLASS_MAP.matcher(src);
                String prefix = c.find() ? c.group(1) : "";
                Matcher m = METHOD_MAP.matcher(src);
                while (m.find()) out.add(prefix + (m.group(1) == null ? "" : m.group(1)));
            }
        }
        return out;
    }

    @Test
    void everySecurityEndpointIsCoveredByTheSecurityModule() throws IOException {
        List<String> paths = allPaths(Path.of("src/main/java/za/co/handyflow/platform/security/api"));
        assertTrue(paths.size() > 100, "parsed too few mappings: " + paths.size());

        TreeSet<String> uncovered = new TreeSet<>();
        for (String path : paths) {
            if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) continue;
            // path variables do not change the prefix match
            if (!"security".equals(ModuleEntitlementPaths.moduleFor(path).orElse(null))) uncovered.add(path);
        }
        assertEquals(new TreeSet<String>(), uncovered,
                "Security endpoints outside /api/v1/security/ and /api/v1/guard/ (add the prefix to ModuleEntitlementPaths "
                        + "or, if public on purpose, to PUBLIC_PREFIXES):");
    }

    @Test
    void securityPrefixesStayMapped() {
        assertEquals("security", ModuleEntitlementPaths.moduleFor("/api/v1/security/payroll/runs").orElse(null));
        assertEquals("security", ModuleEntitlementPaths.moduleFor("/api/v1/guard/incidents").orElse(null));
    }
}
