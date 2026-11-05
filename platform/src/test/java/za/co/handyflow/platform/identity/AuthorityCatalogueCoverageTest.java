package za.co.handyflow.platform.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IAM Phase 1 rule: no controller may guard an endpoint with an authority that is not registered in the permission
 * catalogue. An unregistered name can never be granted to any role, so the endpoint would be unreachable for everyone
 * (or, worse, someone later "fixes" it by loosening the guard). The catalogue is the set of names inserted into
 * {@code permissions} by the Flyway migrations. Scans source files, so it needs no database.
 */
class AuthorityCatalogueCoverageTest {

    private static final Path MAIN = Path.of("src/main");

    /** Authorities issued by a filter at runtime, not stored in the catalogue. */
    private static final Set<String> SYNTHETIC = Set.of(
            "PORTAL_USER"   // PortalJwtFilter: every external portal session
    );

    private static final Pattern PRE_AUTH = Pattern.compile("@PreAuthorize\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");
    private static final Pattern HAS_AUTH = Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)");
    private static final Pattern QUOTED = Pattern.compile("'([A-Za-z0-9_:.\\-]+)'");
    private static final Pattern CATALOGUE_ROW = Pattern.compile(
            "\\(\\s*(?:gen_random_uuid\\(\\)|uuid_generate_v4\\(\\))\\s*,\\s*'([A-Z][A-Z0-9_]+)'");

    private static Set<String> readCatalogue() throws IOException {
        Set<String> catalogue = new HashSet<>();
        try (Stream<Path> files = Files.walk(MAIN.resolve("resources/db/migration"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(f);
                if (!sql.toUpperCase(Locale.ROOT).contains("INSERT INTO PERMISSIONS")) continue;
                Matcher m = CATALOGUE_ROW.matcher(sql);
                while (m.find()) catalogue.add(m.group(1));
            }
        }
        return catalogue;
    }

    /** Flyway version of a migration file name, as comparable numbers (V375_3__x.sql is 375.3). */
    private static double version(Path f) {
        Matcher m = Pattern.compile("^V([0-9]+)(?:_([0-9]+))?__").matcher(f.getFileName().toString());
        if (!m.find()) return -1;
        return Double.parseDouble(m.group(1) + (m.group(2) == null ? "" : "." + m.group(2)));
    }

    /**
     * IAM Phase 1 rule: every permission carries metadata (owning module, sensitivity, second-person flag, who may grant
     * it). V590 fills it for the permissions that existed then; a permission added later must be given metadata by a
     * migration numbered V590 or higher that mentions module_key, otherwise it stays 'unassigned' and is never entitled.
     */
    @Test
    void everyPermissionInTheCatalogueHasMetadata() throws IOException {
        Set<String> catalogue = readCatalogue();
        StringBuilder metadata = new StringBuilder();
        try (Stream<Path> files = Files.walk(MAIN.resolve("resources/db/migration"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(f);
                if (version(f) >= 590 && sql.contains("module_key")) metadata.append(sql);
            }
        }
        assertTrue(metadata.length() > 0, "expected the permission metadata migration (V590) to be present");
        SortedSet<String> without = new TreeSet<>();
        for (String name : catalogue) if (!metadata.toString().contains("'" + name + "'")) without.add(name);
        assertEquals(Set.of(), without, "permissions in the catalogue with no module_key metadata");
    }

    @Test
    void everyAuthorityUsedByAControllerIsInThePermissionCatalogue() throws IOException {
        Set<String> catalogue = readCatalogue();
        // checked against a real database built from the migrations: exactly these rows (397 at 2026-11-05)
        assertTrue(catalogue.size() > 300, "expected to read the catalogue from the migrations, found " + catalogue.size());

        SortedMap<String, SortedSet<String>> unregistered = new TreeMap<>();
        int used = 0;
        try (Stream<Path> files = Files.walk(MAIN.resolve("java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f);
                Matcher pre = PRE_AUTH.matcher(src);
                while (pre.find()) {
                    Matcher has = HAS_AUTH.matcher(pre.group(1));
                    while (has.find()) {
                        Matcher q = QUOTED.matcher(has.group(1));
                        while (q.find()) {
                            used++;
                            String name = q.group(1);
                            if (!catalogue.contains(name) && !SYNTHETIC.contains(name)) {
                                unregistered.computeIfAbsent(name, k -> new TreeSet<>()).add(f.getFileName().toString());
                            }
                        }
                    }
                }
            }
        }
        assertTrue(used > 500, "expected to find the controller guards, found " + used);
        assertEquals(Map.of(), unregistered, "authorities used in @PreAuthorize but not registered in the permission catalogue");
    }
}
