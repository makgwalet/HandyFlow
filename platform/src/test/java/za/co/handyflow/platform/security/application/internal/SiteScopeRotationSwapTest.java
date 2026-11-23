package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SiteScopeRotationSwapTest {

    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";
    private static final String KIND = "SiteScopeService.Kind.";
    private static final String FQ_KIND = "za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind.";

    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }

    /** Every endpoint method of a controller: name -> body (from the opening brace to the matching closing one). */
    private static Map<String, String> endpoints(String file) throws Exception {
        String s = read("api/" + file + ".java");
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping").matcher(s);
        while (m.find()) {
            Matcher sig = Pattern.compile("public [^;{]*?\\b(\\w+)\\(").matcher(s);
            assertTrue(sig.find(m.end()), "no method after mapping");
            int i = sig.end(), depth = 1;
            while (depth > 0) { char c = s.charAt(i++); if (c == '(') depth++; if (c == ')') depth--; }
            int open = s.indexOf('{', i), d = 0, j = open;
            for (; j < s.length(); j++) { char c = s.charAt(j); if (c == '{') d++; if (c == '}' && --d == 0) break; }
            out.put(sig.group(1), s.substring(open + 1, j));
        }
        return out;
    }

    private static String method(String src, String name) {
        int at = name.contains("(") ? src.indexOf(name) : -1;
        if (at < 0) {
            Matcher mm = Pattern.compile("(public|private|static)[^;=(]*\\s" + Pattern.quote(name) + "\\(").matcher(src);
            assertTrue(mm.find(), "missing " + name);
            at = mm.start();
        }
        int open = src.indexOf('{', at), d = 0, j = open;
        for (; j < src.length(); j++) { char c = src.charAt(j); if (c == '{') d++; if (c == '}' && --d == 0) break; }
        return src.substring(at, j + 1);
    }

    // ---- the record kinds ----

    @Test void theNewKindsAreFixedTenantScopedQueries() throws Exception {
        String s = read("application/internal/SiteScopeService.java");
        String e = s.substring(s.indexOf("public enum Kind"), s.indexOf("final String sql;"));
        assertTrue(e.contains("ROTATION_PATTERN(\"security_rotation_patterns\")"));
        assertTrue(e.contains("ROTATION_ASSIGNMENT(\"SELECT p.site_id FROM security_rotation_assignments a JOIN security_rotation_patterns p ON p.id = a.pattern_id AND p.tenant_id = a.tenant_id \"\n                + \"WHERE a.id = ? AND a.tenant_id = ?\")"));
        assertTrue(e.contains("SWAP(\"SELECT s.site_id FROM security_shift_swap_requests r JOIN security_shifts s ON s.id = r.original_shift_id AND s.tenant_id = r.tenant_id \"\n                + \"WHERE r.id = ? AND r.tenant_id = ?\")"));
    }

    // ---- rotation ----

    private static final Map<String, String> ROTATION = new LinkedHashMap<>();
    static {
        ROTATION.put("createPattern", "site(req.siteId());");
        ROTATION.put("updatePattern", "child(" + KIND + "ROTATION_PATTERN, id);");
        ROTATION.put("deactivatePattern", "child(" + KIND + "ROTATION_PATTERN, id);");
        ROTATION.put("assignGuard", "child(" + KIND + "ROTATION_PATTERN, req.patternId());");
        ROTATION.put("getAssignmentsForPattern", "child(" + KIND + "ROTATION_PATTERN, patternId);");
        ROTATION.put("endAssignment", "child(" + KIND + "ROTATION_ASSIGNMENT, assignmentId);");
        ROTATION.put("generateSchedule", "child(" + KIND + "ROTATION_PATTERN, req.patternId());");
    }

    @Test void everyRotationEndpointIsAccountedFor() throws Exception {
        Map<String, String> m = endpoints("RotationController");
        List<String> expected = new ArrayList<>(ROTATION.keySet()); expected.add("getPatterns");
        assertEquals(expected.stream().sorted().toList(), m.keySet().stream().sorted().toList());
    }

    @Test void rotationEndpointsCheckTheirSiteBeforeDoingAnything() throws Exception {
        Map<String, String> m = endpoints("RotationController");
        for (var e : ROTATION.entrySet()) {
            String body = m.get(e.getKey()).replace(FQ_KIND, KIND);
            assertTrue(body.contains(e.getValue()), e.getKey());
            assertTrue(body.indexOf(e.getValue()) < body.indexOf("rotationService."), e.getKey() + ": check must come before the service call");
        }
    }

    @Test void theRotationListIsFilteredNotBlocked() throws Exception {
        String body = endpoints("RotationController").get("getPatterns");
        assertTrue(body.contains("rotationService.getPatterns(tenantId, pageable, allowed())"));
        assertFalse(body.contains("wholeCompany();"));
    }

    // ---- swaps ----

    private static final Map<String, String> SWAPS = new LinkedHashMap<>();
    static {
        SWAPS.put("createSwapRequest", "child(" + KIND + "SHIFT, req.originalShiftId());");
        for (String n : new String[]{"acceptSwap", "approveSwap", "rejectSwap", "cancelSwap"}) SWAPS.put(n, "child(" + KIND + "SWAP, id);");
    }

    @Test void everySwapEndpointIsAccountedFor() throws Exception {
        Map<String, String> m = endpoints("ShiftSwapController");
        List<String> expected = new ArrayList<>(SWAPS.keySet()); expected.add("getPendingSwaps"); expected.add("getSwapsByGuard");
        assertEquals(expected.stream().sorted().toList(), m.keySet().stream().sorted().toList());
    }

    @Test void swapEndpointsCheckTheShiftsSiteBeforeDoingAnything() throws Exception {
        Map<String, String> m = endpoints("ShiftSwapController");
        for (var e : SWAPS.entrySet()) {
            String body = m.get(e.getKey()).replace(FQ_KIND, KIND);
            assertTrue(body.contains(e.getValue()), e.getKey());
            assertTrue(body.indexOf(e.getValue()) < body.indexOf("swapService."), e.getKey());
        }
    }

    @Test void theSwapListsAreFiltered() throws Exception {
        Map<String, String> m = endpoints("ShiftSwapController");
        assertTrue(m.get("getPendingSwaps").contains("swapService.getPendingSwaps(tenantId, pageable, allowed())"));
        assertTrue(m.get("getSwapsByGuard").contains("swapService.getSwapsByGuard(tenantId, guardId, pageable, allowed())"));
    }

    // ---- services and repositories ----

    @Test void theServicesFilterByAllowedSites() throws Exception {
        String r = read("application/internal/RotationService.java");
        String list = method(r, "getPatterns(TenantId tenantId, Pageable pageable, java.util.Set<UUID> allowedSites)");
        assertTrue(list.contains("if (allowedSites == null) return patternRepository.findAllActive("));
        assertTrue(list.contains("if (allowedSites.isEmpty()) return Page.empty(pageable);"));
        assertTrue(list.contains("patternRepository.findAllActiveInSites(tenantId, allowedSites, pageable)"));
        assertTrue(r.contains("return getPatterns(tenantId, pageable, null);"), "the old signature still means the whole company");
        String s = read("application/internal/ShiftSwapService.java");
        String pend = method(s, "getPendingSwaps(TenantId tenantId, Pageable pageable, java.util.Set<UUID> allowedSites)");
        assertTrue(pend.contains("if (allowedSites == null) return swapRepository.findPending("));
        assertTrue(pend.contains("if (allowedSites.isEmpty()) return Page.empty(pageable);"));
        assertTrue(pend.contains("swapRepository.findPendingInSites(tenantId, allowedSites, pageable)"));
        String byG = method(s, "getSwapsByGuard(TenantId tenantId, UUID guardId, Pageable pageable, java.util.Set<UUID> allowedSites)");
        assertTrue(byG.contains("if (allowedSites == null) return swapRepository.findByGuard("));
        assertTrue(byG.contains("if (allowedSites.isEmpty()) return Page.empty(pageable);"));
        assertTrue(byG.contains("swapRepository.findByGuardInSites(tenantId, guardId, allowedSites, pageable)"));
        assertTrue(s.contains("return getPendingSwaps(tenantId, pageable, null);") && s.contains("return getSwapsByGuard(tenantId, guardId, pageable, null);"));
    }

    @Test void theLimitedQueriesAreTenantScopedAndKeepTheirFilters() throws Exception {
        String swap = read("domain/repository/ShiftSwapRepository.java");
        String p = swap.substring(swap.lastIndexOf("@Query", swap.indexOf("findPendingInSites")), swap.indexOf("findPendingInSites"));
        assertTrue(p.contains("r.tenantId = :tenantId") && p.contains("r.status IN ('PENDING', 'PROPOSED_ACCEPTED')"));
        assertTrue(p.contains("r.originalShiftId IN (SELECT s.id FROM Shift s WHERE s.tenantId = :tenantId AND s.siteId IN :siteIds)"));
        assertTrue(p.contains("ORDER BY r.requestedAt"));
        String g = swap.substring(swap.lastIndexOf("@Query", swap.indexOf("findByGuardInSites")), swap.indexOf("findByGuardInSites"));
        assertTrue(g.contains("r.tenantId = :tenantId") && g.contains("(r.requestingGuardId = :guardId OR r.proposedGuardId = :guardId)"));
        assertTrue(g.contains("r.originalShiftId IN (SELECT s.id FROM Shift s WHERE s.tenantId = :tenantId AND s.siteId IN :siteIds)"));
        String pat = read("domain/repository/RotationPatternRepository.java");
        String q = pat.substring(pat.lastIndexOf("@Query", pat.indexOf("findAllActiveInSites")), pat.indexOf("findAllActiveInSites"));
        assertTrue(q.contains("p.tenantId = :tenantId") && q.contains("p.active = true") && q.contains("p.siteId IN :siteIds") && q.contains("ORDER BY p.name"));
    }

    // ---- company-wide controllers ----

    @Test void armouryCloseProtectionAndVettingAreWholeCompanyOnly() throws Exception {
        int[] want = {14, 41, 6};
        String[] files = {"ArmouryController", "CloseProtectionController", "VettingController"};
        for (int k = 0; k < files.length; k++) {
            Map<String, String> m = endpoints(files[k]);
            assertEquals(want[k], m.size(), files[k] + " endpoint count (a new endpoint must be added to this test)");
            for (var e : m.entrySet()) assertTrue(e.getValue().stripLeading().startsWith("wholeCompany();"), files[k] + "." + e.getKey());
            String s = read("api/" + files[k] + ".java");
            assertTrue(method(s, "wholeCompany()").contains("SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId())"));
            assertTrue(s.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)"));
        }
    }
}
