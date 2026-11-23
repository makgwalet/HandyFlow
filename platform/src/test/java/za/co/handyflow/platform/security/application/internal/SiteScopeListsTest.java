package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SiteScopeListsTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";

    private static String src(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }

    // ── the SQL pieces ──────────────────────────────────────────────────────

    @Test
    void theWholeCompanyAddsNothing() {
        assertEquals("", SiteScopeRules.sqlFilter("i.site_id", null));
        assertEquals(List.of(), SiteScopeRules.sqlArgs(null));
    }

    @Test
    void aLimitedUserGetsOnePlaceholderPerSite() {
        assertEquals(" AND i.site_id IN (?)", SiteScopeRules.sqlFilter("i.site_id", Set.of(A)));
        Set<UUID> two = new LinkedHashSet<>(List.of(A, B));
        assertEquals(" AND i.site_id IN (?, ?)", SiteScopeRules.sqlFilter("i.site_id", two));
        assertEquals(List.of(A, B), SiteScopeRules.sqlArgs(two));
    }

    @Test
    void anEmptyScopeMatchesNothingRatherThanEverything() {
        assertEquals(" AND 1 = 0", SiteScopeRules.sqlFilter("i.site_id", Set.of()));
        assertEquals(List.of(), SiteScopeRules.sqlArgs(Set.of()));
        // never an empty IN () list, which is a syntax error, and never no filter at all
        assertFalse(SiteScopeRules.sqlFilter("i.site_id", Set.of()).contains("IN ()"));
    }

    @Test
    void placeholdersAndValuesAlwaysMatchInNumber() {
        Set<UUID> many = new LinkedHashSet<>();
        for (int i = 0; i < 37; i++) many.add(UUID.randomUUID());
        String f = SiteScopeRules.sqlFilter("x", many);
        assertEquals(37L, f.chars().filter(c -> c == '?').count());
        assertEquals(37, SiteScopeRules.sqlArgs(many).size());
    }

    // ── the services ────────────────────────────────────────────────────────

    @Test
    void incidentsAreFilteredInSqlBeforeTheCountAndPageAreBuilt() throws Exception {
        String s = src("application/internal/IncidentService.java");
        int f = s.indexOf("where.append(SiteScopeRules.sqlFilter(\"i.site_id\", allowedSites));");
        int a = s.indexOf("params.addAll(SiteScopeRules.sqlArgs(allowedSites));");
        int count = s.indexOf("String countSql");
        int pageArgs = s.indexOf("pageParams.add(pageable.getPageSize());");
        assertTrue(f > 0 && a > f && count > a && pageArgs > count, "filter text, then its values, then the SQL, then paging values");
        assertTrue(s.contains("return getIncidents(tenantId, status, severity, pageable, null);"), "the old signature still means the whole company");
        assertEquals(1, count(s, "SiteScopeRules.sqlFilter"));
    }

    @Test
    void shiftsAreFilteredByRepositoryForPagesAndInMemoryForTheBoundedRange() throws Exception {
        String s = src("application/internal/ShiftService.java");
        assertTrue(s.contains("if (allowedSites == null) return shiftRepository.findAllActive(tenantId, pageable).map(this::toResponse);"));
        assertTrue(s.contains("if (allowedSites.isEmpty()) return Page.empty(pageable);"));
        assertTrue(s.contains("shiftRepository.findAllActiveInSites(tenantId, allowedSites, pageable)"));
        assertTrue(s.contains("SiteScopeRules.keep(allowedSites, shiftRepository.findByTenantInRange(tenantId, from, to).stream().map(this::toResponse).toList(), ShiftResponse::siteId)"));
        int validate = s.indexOf("RANGE_TOO_LONG");
        int keep = s.indexOf("SiteScopeRules.keep(allowedSites");
        assertTrue(validate > 0 && keep > validate, "a bad window is refused before anything is returned");
        assertTrue(s.contains("return getShifts(tenantId, pageable, null);") && s.contains("return getRange(tenantId, from, to, null);"));
    }

    @Test
    void camerasAlarmsAndDispatchesFollowTheSamePattern() throws Exception {
        String c = src("application/internal/CameraService.java");
        assertTrue(c.contains("if (allowedSites == null) return cameraRepository.findAllActive(tenantId, pageable)"));
        assertTrue(c.contains("if (allowedSites.isEmpty()) return Page.empty(pageable);"));
        assertTrue(c.contains("cameraRepository.findAllActiveInSites(tenantId, allowedSites, pageable)"));
        String r = src("application/internal/ControlRoomService.java");
        assertTrue(r.contains("if (allowedSites == null) return eventRepository.findOpenQueue(tenantId, pageable);"));
        assertTrue(r.contains("if (allowedSites.isEmpty()) return Page.empty(pageable);"));
        assertTrue(r.contains("eventRepository.findOpenQueueInSites(tenantId, allowedSites, pageable)"));
        assertTrue(r.contains("if (allowedSites == null) return dispatchRepository.findOpen(tenantId);"));
        assertTrue(r.contains("if (allowedSites.isEmpty()) return List.of();"));
        assertTrue(r.contains("dispatchRepository.findOpenInSites(tenantId, allowedSites)"));
    }

    @Test
    void theNewQueriesAreTenantScopedAndKeepTheOriginalConditions() throws Exception {
        String[][] q = {
                {"domain/repository/ShiftRepository.java", "findAllActiveInSites", "s.tenantId = :tenantId", "s.siteId IN :siteIds", "s.deletedAt IS NULL", "ORDER BY s.startAt DESC"},
                {"domain/repository/CameraRepository.java", "findAllActiveInSites", "c.tenantId = :tenantId", "c.siteId IN :siteIds", "c.status != 'DECOMMISSIONED'", "ORDER BY c.name"},
                {"domain/repository/AlarmEventRepository.java", "findOpenQueueInSites", "e.tenantId = :tenantId", "e.siteId IN :siteIds", "e.status IN ('NEW', 'TRIAGED', 'DISPATCHED')", "ORDER BY e.createdAt DESC"},
                {"domain/repository/DispatchRepository.java", "findOpenInSites", "d.tenantId = :tenantId", "e.siteId IN :siteIds", "d.resolvedAt IS NULL", "e.tenantId = :tenantId"},
        };
        for (String[] row : q) {
            String s = src(row[0]);
            int m = s.indexOf(row[1]);
            assertTrue(m > 0, row[1]);
            String query = s.substring(s.lastIndexOf("@Query(\"\"\"", m), m);
            for (int i = 2; i < row.length; i++) assertTrue(query.contains(row[i]), row[1] + " lacks " + row[i]);
        }
    }

    // ── the controllers ─────────────────────────────────────────────────────

    @Test
    void theControllersPassTheUsersSitesAndNoLongerBlockTheLists() throws Exception {
        String[][] calls = {
                {"api/IncidentController.java", "incidentService.getIncidents(\n                        TenantContext.getTenantIdAsObject(), status, severity, pageable, allowed)"},
                {"api/ShiftController.java", "shiftService.getShifts(TenantContext.getTenantIdAsObject(), pageable, allowed)"},
                {"api/ShiftController.java", "shiftService.getRange(TenantContext.getTenantIdAsObject(), from, to, allowed)"},
                {"api/CameraController.java", "cameraService.getAllForTenant(tenantId, pageable, allowed)"},
                {"api/ControlRoomController.java", "controlRoomService.getOpenQueue(tenantId, pageable, allowed)"},
                {"api/ControlRoomController.java", "controlRoomService.getOpenDispatches(tenantId, allowed)"},
        };
        for (String[] c : calls) {
            String s = src(c[0]);
            assertTrue(s.contains(c[1]), c[0] + " must call " + c[1]);
            int at = s.indexOf(c[1]);
            String before = s.substring(Math.max(0, at - 220), at);
            assertTrue(before.contains("java.util.Set<UUID> allowed = allowed();"), c[0] + ": allowed must be read first");
            assertFalse(before.contains("wholeCompany();"), c[0] + " still blocks the list");
        }
        for (String f : new String[]{"IncidentController", "ShiftController", "CameraController", "ControlRoomController"}) {
            String s = src("api/" + f + ".java");
            assertTrue(s.contains("private java.util.Set<UUID> allowed() {\n        return SiteScopeService.allowedSites(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());"), f);
        }
    }

    private static int count(String s, String what) {
        int n = 0, i = 0;
        while ((i = s.indexOf(what, i)) >= 0) { n++; i += what.length(); }
        return n;
    }
}
