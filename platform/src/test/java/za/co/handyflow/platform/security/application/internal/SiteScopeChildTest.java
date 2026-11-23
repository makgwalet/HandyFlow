package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SiteScopeChildTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final String API = "src/main/java/za/co/handyflow/platform/security/api/";

    private static String code(Runnable r) { return assertThrows(HandyFlowException.class, r::run).getErrorCode(); }

    // ── rule ────────────────────────────────────────────────────────────────

    @Test
    void aUserWithTheWholeCompanyNeverTripsOnARecord() {
        assertDoesNotThrow(() -> SiteScopeRules.requireChild(null, true, A));
        assertDoesNotThrow(() -> SiteScopeRules.requireChild(null, true, null));
        assertDoesNotThrow(() -> SiteScopeRules.requireChild(null, false, null));
    }

    @Test
    void aRecordAtAnAllowedSitePasses() {
        assertDoesNotThrow(() -> SiteScopeRules.requireChild(Set.of(A), true, A));
    }

    @Test
    void aRecordAtAnotherSiteIsNotFound() {
        assertEquals("NOT_FOUND", code(() -> SiteScopeRules.requireChild(Set.of(A), true, B)));
        HandyFlowException e = SiteScopeRules.childNotFound();
        assertEquals("NOT_FOUND", e.getStatus().name());
    }

    @Test
    void aRecordWithNoSiteIsOutsideEveryLimitedScope() {
        assertEquals("NOT_FOUND", code(() -> SiteScopeRules.requireChild(Set.of(A), true, null)));
        assertEquals("NOT_FOUND", code(() -> SiteScopeRules.requireChild(Set.of(), true, A)));
    }

    @Test
    void aMissingRecordIsLeftForTheServiceToReport() {
        assertDoesNotThrow(() -> SiteScopeRules.requireChild(Set.of(A), false, null));
        assertDoesNotThrow(() -> SiteScopeRules.requireChild(Set.of(A), false, B));
    }

    // ── the lookup ──────────────────────────────────────────────────────────

    private static String src(String p) throws Exception { return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/" + p)); }

    @Test
    void theLookupUsesFixedQueriesThatAreTenantScoped() throws Exception {
        String s = src("application/internal/SiteScopeService.java");
        String enumText = s.substring(s.indexOf("public enum Kind"), s.indexOf("final String sql;"));
        Matcher kinds = Pattern.compile("([A-Z_]+)\\(\"(security_[a-z_]+)\"\\)").matcher(enumText);
        Map<String, String> found = new LinkedHashMap<>();
        while (kinds.find()) found.put(kinds.group(1), kinds.group(2));
        Map<String, String> want = new LinkedHashMap<>();
        want.put("POST", "security_posts"); want.put("POST_ORDER", "security_post_orders"); want.put("CONTACT", "security_contacts");
        want.put("CAMERA", "security_cameras"); want.put("ALARM_EVENT", "security_alarm_events"); want.put("PATROL_ROUTE", "security_patrol_routes");
        want.put("CHECKPOINT", "security_checkpoints"); want.put("ACCESS_POINT", "security_access_points");
        want.put("GATE_ENTRY", "security_gate_register_entries"); want.put("INCIDENT", "security_incidents"); want.put("SHIFT", "security_shifts");
        want.put("PATROL_ROUND", "security_patrol_rounds"); want.put("ROTATION_PATTERN", "security_rotation_patterns");
        assertEquals(want, found);
        assertTrue(enumText.contains("DISPATCH(\"SELECT e.site_id FROM security_dispatches d JOIN security_alarm_events e ON e.id = d.alarm_event_id AND e.tenant_id = d.tenant_id \"\n                + \"WHERE d.id = ? AND d.tenant_id = ?\")"));
        assertTrue(enumText.contains("ROTATION_ASSIGNMENT(\"SELECT p.site_id FROM security_rotation_assignments a JOIN security_rotation_patterns p ON p.id = a.pattern_id AND p.tenant_id = a.tenant_id \"\n                + \"WHERE a.id = ? AND a.tenant_id = ?\")"));
        assertTrue(enumText.contains("SWAP(\"SELECT s.site_id FROM security_shift_swap_requests r JOIN security_shifts s ON s.id = r.original_shift_id AND s.tenant_id = r.tenant_id \"\n                + \"WHERE r.id = ? AND r.tenant_id = ?\")"));
        assertTrue(s.contains("\"SELECT site_id FROM \" + tableOrSql + \" WHERE id = ? AND tenant_id = ?\""));
        assertTrue(s.contains("s.jdbc.query(kind.sql,"));
        assertTrue(s.contains("tableOrSql.startsWith(\"SELECT \") ? tableOrSql :"), "a table name becomes the standard tenant-scoped query; a full query is used as written");
        assertFalse(s.contains("kind.name()"), "the query never comes from a name or a request");
    }

    @Test
    void theLookupIsSkippedForAnUnrestrictedUserAndNullSafe() throws Exception {
        String s = src("application/internal/SiteScopeService.java");
        int at = s.indexOf("public static void checkChild");
        String body = s.substring(at, s.indexOf("    /** A record whose site is optional"));
        assertTrue(body.contains("if (s == null) return;") && body.indexOf("if (s == null) return;") < body.indexOf("allowedSites("));
        assertTrue(body.contains("if (allowed == null) return;"), "an unrestricted user must not pay for the lookup");
        assertTrue(body.indexOf("if (allowed == null) return;") < body.indexOf("s.jdbc.query"));
        assertTrue(body.contains("SiteScopeRules.requireChild(allowed, !rows.isEmpty(), rows.isEmpty() ? null : rows.get(0));"));
    }

    @Test
    void anOptionalSiteMeansTheWholeCompany() throws Exception {
        String s = src("application/internal/SiteScopeService.java");
        int at = s.indexOf("public static void checkOptionalSite");
        String body = s.substring(at, s.indexOf("    /** Optional-site lists:"));
        assertTrue(body.contains("if (siteId == null) SiteScopeRules.requireUnrestricted("));
        assertTrue(body.contains("else SiteScopeRules.requireSite("));
    }

    // ── the controllers ──────────────────────────────────────────────────────

    private record Method(String name, String signature, String body) {}

    private static List<Method> methods(String file) throws Exception {
        String[] lines = Files.readString(Path.of(API + file + ".java")).split("\n");
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) if (lines[i].matches("    @(Get|Post|Put|Delete|Patch)Mapping.*")) starts.add(i);
        List<Method> out = new ArrayList<>();
        for (int k = 0; k < starts.size(); k++) {
            int end = k + 1 < starts.size() ? starts.get(k + 1) : lines.length;
            int pub = -1;
            for (int i = starts.get(k); i < end; i++) if (lines[i].startsWith("    public ")) { pub = i; break; }
            int j = pub;
            while (!lines[j].stripTrailing().endsWith(") {")) j++;
            StringBuilder sig = new StringBuilder(), body = new StringBuilder();
            for (int i = pub; i <= j; i++) sig.append(lines[i]).append('\n');
            for (int i = j + 1; i < end; i++) body.append(lines[i]).append('\n');
            Matcher m = Pattern.compile("(\\w+)\\(").matcher(lines[pub]);
            m.find();
            out.add(new Method(m.group(1), sig.toString(), body.toString()));
        }
        return out;
    }

    /** controller -> method -> the exact check it must start with. */
    private static final Map<String, Map<String, String>> EXPECTED = new LinkedHashMap<>();
    static {
        EXPECTED.put("PostOrderController", Map.of("createContact", "optionalSite(req.siteId());", "updateContact", "child(SiteScopeService.Kind.CONTACT, id);",
                "deactivateContact", "child(SiteScopeService.Kind.CONTACT, id);", "updatePost", "child(SiteScopeService.Kind.POST, id);",
                "deactivatePost", "child(SiteScopeService.Kind.POST, id);", "updateDraft", "child(SiteScopeService.Kind.POST_ORDER, id);",
                "publish", "child(SiteScopeService.Kind.POST_ORDER, id);", "addAttachment", "child(SiteScopeService.Kind.POST_ORDER, id);",
                "getAcknowledgements", "child(SiteScopeService.Kind.POST_ORDER, id);"));
        EXPECTED.put("CameraController", Map.of("getById", "child(SiteScopeService.Kind.CAMERA, id);", "update", "child(SiteScopeService.Kind.CAMERA, id);",
                "markOffline", "child(SiteScopeService.Kind.CAMERA, id);", "markActive", "child(SiteScopeService.Kind.CAMERA, id);",
                "decommission", "child(SiteScopeService.Kind.CAMERA, id);", "generateWebhookSecret", "child(SiteScopeService.Kind.CAMERA, id);",
                "register", "site(req.siteId());"));
        EXPECTED.put("ControlRoomController", Map.of("ingest", "optionalSite(req.siteId());", "triage", "child(SiteScopeService.Kind.ALARM_EVENT, id);",
                "markFalseAlarm", "child(SiteScopeService.Kind.ALARM_EVENT, id);", "dispatch", "child(SiteScopeService.Kind.ALARM_EVENT, id);",
                "getDispatchesForEvent", "child(SiteScopeService.Kind.ALARM_EVENT, id);", "getOpenDispatches", "java.util.Set<UUID> allowed = allowed();",
                "recordArrival", "child(SiteScopeService.Kind.DISPATCH, id);", "resolveDispatch", "child(SiteScopeService.Kind.DISPATCH, id);"));
        EXPECTED.put("PatrolController", Map.of("detail", "child(SiteScopeService.Kind.PATROL_ROUND, id);", "acknowledge", "child(SiteScopeService.Kind.PATROL_ROUND, id);"));
        EXPECTED.put("PatrolRouteController", Map.of("addCheckpointToRoute", "child(SiteScopeService.Kind.PATROL_ROUTE, routeId);",
                "getRoundsForShift", "child(SiteScopeService.Kind.SHIFT, shiftId);"));
        EXPECTED.put("CheckpointAdminController", Map.of("update", "child(SiteScopeService.Kind.CHECKPOINT, id);"));
        EXPECTED.put("GateAccessController", Map.of("createAccessPoint", "site(request.siteId());", "updateAccessPoint", "child(SiteScopeService.Kind.ACCESS_POINT, id);",
                "deactivateAccessPoint", "child(SiteScopeService.Kind.ACCESS_POINT, id);", "reactivateAccessPoint", "child(SiteScopeService.Kind.ACCESS_POINT, id);",
                "getAttachments", "child(SiteScopeService.Kind.GATE_ENTRY, entryId);"));
        Map<String, String> incident = new LinkedHashMap<>();
        for (String m : new String[]{"acknowledge", "resolve", "get", "assign", "escalate", "note", "reopen", "attach", "download", "removeFile", "pdf"}) incident.put(m, "child(SiteScopeService.Kind.INCIDENT, id);");
        incident.put("getIncidents", "java.util.Set<UUID> allowed = allowed();");
        incident.put("createIncident", "site(req.siteId());");
        EXPECTED.put("IncidentController", incident);
        Map<String, String> shift = new LinkedHashMap<>();
        for (String m : new String[]{"updateShift", "startShift", "completeShift", "dismissNoShow", "closeOvertime", "pullFromSite", "cancelShift"}) shift.put(m, "child(SiteScopeService.Kind.SHIFT, id);");
        shift.put("getShifts", "java.util.Set<UUID> allowed = allowed();");
        shift.put("getRange", "java.util.Set<UUID> allowed = allowed();");
        shift.put("createShift", "site(request.siteId());");
        EXPECTED.put("ShiftController", shift);
    }

    @Test
    void everyListedMethodStartsWithItsCheck() throws Exception {
        int total = 0;
        for (var c : EXPECTED.entrySet()) {
            List<Method> ms = methods(c.getKey());
            for (var e : c.getValue().entrySet()) {
                Method m = ms.stream().filter(x -> x.name().equals(e.getKey())).findFirst().orElseThrow(() -> new AssertionError(c.getKey() + "." + e.getKey() + " missing"));
                assertEquals(1, count(m.body(), e.getValue()), c.getKey() + "." + e.getKey() + " must call " + e.getValue());
                String before = m.body().substring(0, m.body().indexOf(e.getValue()));
                assertFalse(before.contains("Service.") || before.contains("service."), c.getKey() + "." + e.getKey() + " does work before the scope check");
                total++;
            }
        }
        assertEquals(57, total);
    }

    /** Endpoints with an id in the path that are knowingly not scoped yet. Keeping this list honest is the point of the test below. */
    private static final Map<String, Set<String>> NOT_YET = Map.of();

    @Test
    void noPathIdInTheseControllersIsLeftUncheckedByAccident() throws Exception {
        for (String f : EXPECTED.keySet()) {
            for (Method m : methods(f)) {
                boolean hasId = Pattern.compile("@PathVariable UUID (?!siteId)\\w+").matcher(m.signature()).find();
                if (!hasId) continue;
                if (NOT_YET.getOrDefault(f, Set.of()).contains(m.name())) continue;
                assertTrue(m.body().contains("child(") || m.body().contains("site(siteId);"), f + "." + m.name() + " takes an id but never checks its site");
            }
        }
    }

    @Test
    void theKnownGapsReallyAreGaps() throws Exception {
        for (var e : NOT_YET.entrySet()) {
            for (String name : e.getValue()) {
                Method m = methods(e.getKey()).stream().filter(x -> x.name().equals(name)).findFirst().orElseThrow();
                assertFalse(m.body().contains("child(") || m.body().contains("site("), name + " is listed as a gap but is checked; remove it from the list");
            }
        }
    }

    @Test
    void everyControllerHasTheOptionalCollaboratorAndAllHelpers() throws Exception {
        for (String f : EXPECTED.keySet()) {
            String c = Files.readString(Path.of(API + f + ".java"));
            assertTrue(c.contains("import za.co.handyflow.platform.security.application.internal.SiteScopeService;"), f);
            assertTrue(c.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)\n    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;"), f);
            for (String h : new String[]{"SiteScopeService.check(scope,", "SiteScopeService.checkUnrestricted(scope,", "SiteScopeService.checkChild(scope,", "SiteScopeService.checkOptionalSite(scope,"}) {
                assertTrue(c.contains(h), f + " lacks " + h);
            }
        }
    }

    @Test
    void theGuardsOwnEndpointsAreStillNotScopedByStaffScope() throws Exception {
        for (String f : new String[]{"GuardIncidentController", "GuardSessionController", "GuardGateAccessController", "GuardDuressController"}) {
            Path p = Path.of(API + f + ".java");
            if (Files.exists(p)) assertFalse(Files.readString(p).contains("SiteScopeService"), f);
        }
    }

    private static int count(String s, String what) {
        int n = 0, i = 0;
        while ((i = s.indexOf(what, i)) >= 0) { n++; i += what.length(); }
        return n;
    }
}
