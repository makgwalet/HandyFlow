package za.co.handyflow.platform.events;

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
 * Source-level guards for the Events module. They read the repository files (no database, no Spring), and exist because
 * each one catches a mistake that would otherwise only show at deploy time or at the door of a live event:
 * a migration that is not SQL, a status the code writes that the database CHECK refuses, an endpoint with no
 * permission guard, a permission that can never be granted.
 */
class EventsSchemaAndSecurityGuardTest {

    private static final Path MAIN = Path.of("src/main");
    private static final Path MIGRATIONS = MAIN.resolve("resources/db/migration");
    private static final Path EVENTS = MAIN.resolve("java/za/co/handyflow/platform/events");

    private static List<Path> sqlFiles() throws IOException {
        try (Stream<Path> s = Files.list(MIGRATIONS)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".sql")).sorted().toList();
        }
    }

    /** Flyway version as text, e.g. "375.10" (V375_10__x.sql) — kept as text so 375.1 and 375.10 stay different. */
    private static String versionKey(Path f) {
        Matcher m = Pattern.compile("^V([0-9]+)(?:_([0-9]+))?__").matcher(f.getFileName().toString());
        if (!m.find()) return "";
        return m.group(1) + (m.group(2) == null ? "" : "." + m.group(2));
    }

    private static double version(Path f) {
        String k = versionKey(f);
        if (k.isEmpty()) return -1;
        String[] parts = k.split("\\.");
        return Double.parseDouble(parts[0]) + (parts.length > 1 ? Integer.parseInt(parts[1]) / 1_000_000.0 : 0);
    }

    // ── Migrations ────────────────────────────────────────────────────────────

    @Test
    void everyMigrationIsSqlNotSourceCode() throws IOException {
        List<String> bad = new ArrayList<>();
        Pattern javaStart = Pattern.compile("(?m)^\\s*(package\\s+[a-z][\\w.]*;|import\\s+[\\w.*]+;|public\\s+(record|class|interface|enum)\\s)");
        for (Path f : sqlFiles()) {
            String sql = Files.readString(f);
            if (javaStart.matcher(sql).find()) bad.add(f.getFileName().toString());
        }
        assertEquals(List.of(), bad, "migration files that contain Java source instead of SQL");
    }

    @Test
    void migrationVersionsAreUnique() throws IOException {
        Map<String, List<String>> byVersion = new TreeMap<>();
        for (Path f : sqlFiles()) byVersion.computeIfAbsent(versionKey(f), k -> new ArrayList<>()).add(f.getFileName().toString());
        Map<String, List<String>> dup = new TreeMap<>();
        byVersion.forEach((v, files) -> { if (!v.isEmpty() && files.size() > 1) dup.put(v, files); });
        assertEquals(Map.of(), dup, "Flyway versions used by more than one file");
    }

    @Test
    void theEventsPaymentTablesAreCreatedByAMigration() throws IOException {
        StringBuilder all = new StringBuilder();
        for (Path f : sqlFiles()) all.append(Files.readString(f).toLowerCase(Locale.ROOT)).append('\n');
        for (String table : List.of("event_payments", "event_refunds", "event_payment_events")) {
            assertTrue(all.indexOf("create table if not exists " + table) >= 0 || all.indexOf("create table " + table) >= 0,
                    "no migration creates " + table);
        }
    }

    /** The status values the code writes must all be allowed by the newest CHECK constraint on that column. */
    @Test
    void guestStatusesWrittenByTheCodeAreAllowedByTheDatabase() throws IOException {
        Set<String> inCode = new TreeSet<>();
        String guest = Files.readString(EVENTS.resolve("domain/model/EventGuest.java"));
        Matcher m = Pattern.compile("this\\.status\\s*=\\s*\"([A-Z_]+)\"").matcher(guest);
        while (m.find()) inCode.add(m.group(1));
        // the default a new guest is created with
        Matcher d = Pattern.compile("g\\.status\\s*=\\s*\"([A-Z_]+)\"").matcher(guest);
        while (d.find()) inCode.add(d.group(1));
        assertTrue(inCode.size() >= 6, "expected to find the guest statuses in EventGuest.java, found " + inCode);

        assertEquals(Set.of(), difference(inCode, newestCheck("event_guests_status_check")),
                "guest statuses the code writes but the database CHECK refuses");
    }

    @Test
    void guestPaymentStatusesWrittenByTheCodeAreAllowedByTheDatabase() throws IOException {
        Set<String> inCode = new TreeSet<>();
        String guest = Files.readString(EVENTS.resolve("domain/model/EventGuest.java"));
        Matcher m = Pattern.compile("paymentStatus\\s*=\\s*(?:[^;\"]*\\?\\s*)?\"([A-Z_]+)\"(?:\\s*:\\s*\"([A-Z_]+)\")?").matcher(guest);
        while (m.find()) { inCode.add(m.group(1)); if (m.group(2) != null) inCode.add(m.group(2)); }
        assertTrue(inCode.contains("PAID") && inCode.contains("PARTIALLY_REFUNDED"), "found " + inCode);
        assertEquals(Set.of(), difference(inCode, newestCheck("event_guests_payment_status_check")),
                "guest payment statuses the code writes but the database CHECK refuses");
    }

    @Test
    void checkInResultsWrittenByTheCodeAreAllowedByTheDatabase() throws IOException {
        String svc = Files.readString(EVENTS.resolve("application/internal/EventsService.java"))
                + Files.readString(EVENTS.resolve("application/internal/GuestOperationsService.java"));
        Set<String> inCode = new TreeSet<>();
        Matcher m = Pattern.compile("result\\s*=\\s*\"([A-Z_]+)\"").matcher(svc);
        while (m.find()) inCode.add(m.group(1));
        Matcher w = Pattern.compile("\"(WALK_IN)\"\\)\\);").matcher(svc);
        while (w.find()) inCode.add(w.group(1));
        assertTrue(inCode.contains("PAYMENT_PENDING") && inCode.contains("SUCCESS"), "found " + inCode);
        assertEquals(Set.of(), difference(inCode, newestCheck("event_check_ins_result_check")),
                "check-in results the code records but the database CHECK refuses");
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>(a);
        out.removeAll(b);
        return out;
    }

    /**
     * Values allowed by the most recent migration that defines the named constraint, either explicitly
     * ("ADD CONSTRAINT name CHECK (...)") or inline in the original V25 table definition.
     */
    private static Set<String> newestCheck(String constraint) throws IOException {
        String table = constraint.startsWith("event_check_ins") ? "event_check_ins" : "event_guests";
        String column = constraint.contains("payment_status") ? "payment_status"
                : constraint.startsWith("event_check_ins") ? "result" : "status";
        Pattern explicit = Pattern.compile("(?is)CONSTRAINT\\s+" + constraint + "\\s+CHECK\\s*\\((.*?)\\)\\s*;");
        Pattern inline = Pattern.compile("(?is)\\b" + column + "\\s+VARCHAR\\(\\d+\\)[^,]*?CHECK\\s*\\(\\s*" + column
                + "\\s+IN\\s*\\((.*?)\\)\\s*\\)");
        Set<String> allowed = null;
        for (Path f : sqlFiles()) {
            String sql = Files.readString(f);
            Matcher e = explicit.matcher(sql);
            while (e.find()) allowed = quoted(e.group(1));
            int i = sql.toLowerCase(Locale.ROOT).indexOf("create table " + table + " ");
            if (i < 0) i = sql.toLowerCase(Locale.ROOT).indexOf("create table " + table + "(");
            if (i >= 0) {
                int end = sql.indexOf(");", i);
                Matcher in = inline.matcher(sql.substring(i, end < 0 ? sql.length() : end));
                if (in.find()) allowed = quoted(in.group(1));
            }
        }
        assertTrue(allowed != null, "could not find the definition of " + constraint);
        return allowed;
    }

    private static Set<String> quoted(String text) {
        Set<String> out = new TreeSet<>();
        Matcher q = Pattern.compile("'([A-Z_]+)'").matcher(text);
        while (q.find()) out.add(q.group(1));
        return out;
    }

    // ── Security ──────────────────────────────────────────────────────────────

    /** Every endpoint of the Events controllers carries a permission guard, except the provider webhook that verifies its own caller. */
    @Test
    void everyEventsEndpointHasAPermissionGuard() throws IOException {
        List<String> unguarded = new ArrayList<>();
        int endpoints = 0;
        try (Stream<Path> files = Files.list(EVENTS.resolve("api"))) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith("Controller.java")).sorted().toList()) {
                if (f.getFileName().toString().equals("PaymentWebhookController.java")) continue;
                String[] lines = Files.readString(f).split("\n");
                boolean classGuard = Files.readString(f).matches("(?s).*@PreAuthorize[^\n]*\n(?:@[^\n]*\n)*\\s*public class.*");
                for (int i = 0; i < lines.length; i++) {
                    if (!lines[i].matches("\\s*@(Get|Post|Put|Delete|Patch)Mapping.*")) continue;
                    endpoints++;
                    boolean guarded = classGuard;
                    for (int j = Math.max(0, i - 4); j <= Math.min(lines.length - 1, i + 4) && !guarded; j++) {
                        if (lines[j].contains("@PreAuthorize")) guarded = true;
                    }
                    if (!guarded) unguarded.add(f.getFileName() + ":" + (i + 1));
                }
            }
        }
        assertTrue(endpoints > 100, "expected to find the Events endpoints, found " + endpoints);
        assertEquals(List.of(), unguarded, "Events endpoints with no @PreAuthorize");
    }

    /** The webhook is open to the provider's servers, so it must never be reachable for a provider that does not verify callbacks. */
    @Test
    void theWebhookOnlyServesGatewaysThatDeclareNotifications() throws IOException {
        String src = Files.readString(EVENTS.resolve("api/PaymentWebhookController.java"));
        assertTrue(src.contains("supportsNotifications"), "the webhook must filter on supportsNotifications()");
        assertTrue(src.contains("rateLimiter.tryConsume"), "the webhook must be rate limited");
        assertTrue(src.contains("parseNotification"), "the webhook must verify the notification through the adapter");
    }

    /** Authorities named in EventsPermissions are used through constants, so the catalogue test that scans literals never sees them. */
    @Test
    void everyAuthorityInEventsPermissionsIsInThePermissionCatalogue() throws IOException {
        Set<String> used = new TreeSet<>();
        Matcher m = Pattern.compile("'(EVENTS_[A-Z_]+)'").matcher(Files.readString(EVENTS.resolve("api/EventsPermissions.java")));
        while (m.find()) used.add(m.group(1));
        assertTrue(used.size() >= 20, "expected the Events authorities, found " + used);

        Set<String> catalogue = new TreeSet<>();
        Pattern row = Pattern.compile("\\(\\s*(?:gen_random_uuid\\(\\)|uuid_generate_v4\\(\\))\\s*,\\s*'([A-Z][A-Z0-9_]+)'");
        StringBuilder metadata = new StringBuilder();
        for (Path f : sqlFiles()) {
            String sql = Files.readString(f);
            if (sql.toUpperCase(Locale.ROOT).contains("INSERT INTO PERMISSIONS")) {
                Matcher r = row.matcher(sql);
                while (r.find()) catalogue.add(r.group(1));
            }
            if (version(f) >= 590 && sql.contains("module_key")) metadata.append(sql);
        }
        Set<String> notRegistered = difference(used, catalogue);
        assertEquals(Set.of(), notRegistered, "Events authorities no role can ever be granted");
        Set<String> noMetadata = new TreeSet<>();
        for (String name : used) if (!metadata.toString().contains("'" + name + "'")) noMetadata.add(name);
        assertEquals(Set.of(), noMetadata, "Events authorities with no module_key metadata");
    }
}
