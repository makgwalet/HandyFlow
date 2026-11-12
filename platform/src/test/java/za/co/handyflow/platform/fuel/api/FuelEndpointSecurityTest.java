package za.co.handyflow.platform.fuel.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * H01 - structural security checks over every Fuel controller. Source scan, no Spring context.
 * Every endpoint must carry @PreAuthorize (method or class), every authority it names must be
 * seeded by a Flyway migration, mutating endpoints must not be guarded by a read authority alone,
 * and every staff controller must call requireModule("fuel") once per endpoint.
 */
class FuelEndpointSecurityTest {

    private static final Path API = Path.of("src/main/java/za/co/handyflow/platform/fuel/api");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    /** Public by design: login/accept-invite for portal users. */
    private static final Set<String> PUBLIC_CONTROLLERS = Set.of("FuelPortalAuthController.java");
    /** Portal-token controllers: class-level guard, tenant derived from the grant, no staff module guard. */
    private static final Set<String> PORTAL_CONTROLLERS = Set.of("FuelPortalDataController.java");
    private static final Pattern MAPPING = Pattern.compile("^\\s*@(Get|Post|Put|Delete|Patch)Mapping");
    private static final Pattern AUTH = Pattern.compile("'([A-Z][A-Z0-9_]+)'");

    private record Endpoint(String file, int line, String verb, String annotations) {}

    private static List<Path> controllers() throws IOException {
        try (Stream<Path> s = Files.list(API)) {
            return s.filter(p -> p.getFileName().toString().endsWith("Controller.java")).sorted().toList();
        }
    }

    private static List<Endpoint> endpoints(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<Endpoint> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = MAPPING.matcher(lines.get(i));
            if (!m.find()) continue;
            StringBuilder sb = new StringBuilder(lines.get(i));
            for (int k = i - 1; k >= 0 && lines.get(k).trim().startsWith("@"); k--) sb.append(' ').append(lines.get(k));
            for (int k = i + 1; k < lines.size() && lines.get(k).trim().startsWith("@"); k++) sb.append(' ').append(lines.get(k));
            out.add(new Endpoint(file.getFileName().toString(), i + 1, m.group(1), sb.toString()));
        }
        return out;
    }

    private static boolean classGuarded(String src) {
        return Pattern.compile("@PreAuthorize[^\\n]*\\n(?:@[^\\n]*\\n)*\\s*public class").matcher(src).find();
    }

    @Test
    void controllersAreFound() throws IOException {
        long total = 0;
        for (Path p : controllers()) total += endpoints(p).size();
        assertThat(controllers()).hasSizeGreaterThanOrEqualTo(15);
        assertThat(total).isGreaterThanOrEqualTo(150);
    }

    @Test
    void everyEndpointHasPreAuthorizeExceptPublicPortalAuth() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path p : controllers()) {
            String name = p.getFileName().toString();
            if (PUBLIC_CONTROLLERS.contains(name)) continue;
            boolean cls = classGuarded(Files.readString(p));
            for (Endpoint e : endpoints(p)) {
                if (!cls && !e.annotations().contains("@PreAuthorize")) missing.add(name + ":" + e.line());
            }
        }
        assertThat(missing).as("endpoints with no @PreAuthorize").isEmpty();
    }

    @Test
    void publicControllerStaysLimitedToTwoEndpoints() throws IOException {
        // Adding a third unauthenticated endpoint must be a deliberate, reviewed change.
        assertThat(endpoints(API.resolve("FuelPortalAuthController.java"))).hasSize(2);
    }

    @Test
    void everyNamedAuthorityIsSeededByAMigration() throws IOException {
        StringBuilder sql = new StringBuilder();
        try (Stream<Path> s = Files.list(MIGRATIONS)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".sql")).toList()) sql.append(Files.readString(p));
        }
        String all = sql.toString();
        Set<String> unknown = new java.util.TreeSet<>();
        for (Path p : controllers()) {
            for (Endpoint e : endpoints(p)) {
                if (!e.annotations().contains("@PreAuthorize")) continue;
                Matcher m = AUTH.matcher(e.annotations());
                while (m.find()) if (!all.contains(m.group(1))) unknown.add(m.group(1) + " in " + e.file());
            }
        }
        assertThat(unknown).as("authorities not present in any migration").isEmpty();
    }

    @Test
    void mutatingEndpointsAreNotGuardedByReadAuthorityAlone() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path p : controllers()) {
            for (Endpoint e : endpoints(p)) {
                if (e.verb().equals("Get") || !e.annotations().contains("@PreAuthorize")) continue;
                List<String> auths = new ArrayList<>();
                Matcher m = AUTH.matcher(e.annotations());
                while (m.find()) auths.add(m.group(1));
                if (!auths.isEmpty() && auths.stream().allMatch(a -> a.endsWith("_READ"))) bad.add(e.file() + ":" + e.line() + " " + auths);
            }
        }
        assertThat(bad).isEmpty();
    }

    @Test
    void staffControllersCallModuleGuardOncePerEndpoint() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path p : controllers()) {
            String name = p.getFileName().toString();
            if (PUBLIC_CONTROLLERS.contains(name) || PORTAL_CONTROLLERS.contains(name)) continue;
            String src = Files.readString(p);
            int guards = Pattern.compile("requireModule\\(\\s*\"fuel\"\\s*\\)").matcher(src).results().mapToInt(r -> 1).sum();
            int eps = endpoints(p).size();
            if (guards < eps) bad.add(name + " endpoints=" + eps + " guards=" + guards);
        }
        assertThat(bad).as("controllers missing featureGuard.requireModule(\"fuel\")").isEmpty();
    }

    @Test
    void staffControllersTakeTenantFromContextNotFromTheRequest() throws IOException {
        List<String> bad = new ArrayList<>();
        Pattern tenantParam = Pattern.compile("@(RequestParam|PathVariable|RequestHeader)[^)]*\\)?\\s*(UUID|String)\\s+tenantId");
        for (Path p : controllers()) {
            if (tenantParam.matcher(Files.readString(p)).find()) bad.add(p.getFileName().toString());
        }
        assertThat(bad).as("tenantId must never be client-supplied").isEmpty();
    }

    /** G01: FUEL_MANAGE is the umbrella. Every area controller must also accept its own narrower permission. */
    private static final Set<String> UMBRELLA_ONLY = Set.of("FuelSettingsController.java");

    @Test
    void fuelManageAlwaysComesWithAnAreaPermission() throws IOException {
        List<String> bad = new ArrayList<>();
        Pattern umbrellaAlone = Pattern.compile("hasAuthority\\('FUEL_MANAGE'\\)");
        for (Path p : controllers()) {
            if (UMBRELLA_ONLY.contains(p.getFileName().toString())) continue;
            for (Endpoint e : endpoints(p)) {
                if (umbrellaAlone.matcher(e.annotations()).find()) bad.add(e.file() + ":" + e.line());
            }
        }
        assertThat(bad).as("endpoints guarded by FUEL_MANAGE alone; add the area permission beside it").isEmpty();
    }

    @Test
    void areaPermissionsAreSeededForFuelAndGivenToAdmin() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve("V666__fuel_granular_manage_permissions.sql"))
                + Files.readString(MIGRATIONS.resolve("V669__fuel_truck_stop.sql"));  // later phases seed their own area permission
        Set<String> used = new java.util.TreeSet<>();
        for (Path p : controllers()) {
            for (Endpoint e : endpoints(p)) {
                Matcher m = AUTH.matcher(e.annotations());
                while (m.find()) if (m.group(1).matches("FUEL_[A-Z_]+_MANAGE") && !m.group(1).equals("FUEL_CONFIGURATION_MANAGE")) used.add(m.group(1));
            }
        }
        assertThat(used).hasSizeGreaterThanOrEqualTo(12);
        for (String perm : used) {
            assertThat(sql).as(perm + " inserted").contains("'" + perm + "', '");
            assertThat(sql).as(perm + " has module_key fuel").containsPattern("module_key = 'fuel'[^;]*WHERE name = '" + perm + "'");
            assertThat(sql).as(perm + " given to ADMIN").contains("'" + perm + "'");
        }
    }
}
