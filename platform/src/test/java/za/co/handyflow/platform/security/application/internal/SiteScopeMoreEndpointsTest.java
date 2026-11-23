package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SiteScopeMoreEndpointsTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final String API = "src/main/java/za/co/handyflow/platform/security/api/";
    private static final String[] COVERED = {"GateAccessController", "PostOrderController", "PatrolRouteController", "CameraController",
            "ControlRoomController", "PatrolController", "GateDashboardController", "LiveOperationsController", "CheckpointAdminController"};

    private static String code(Runnable r) { return assertThrows(HandyFlowException.class, r::run).getErrorCode(); }

    // ── rule ────────────────────────────────────────────────────────────────

    @Test
    void theWholeCompanyMayLeaveTheSiteOut() {
        assertDoesNotThrow(() -> SiteScopeRules.requireChoice(null, null));
        assertDoesNotThrow(() -> SiteScopeRules.requireChoice(null, A));
    }

    @Test
    void aLimitedUserMustNameASiteAndItMustBeTheirs() {
        Set<UUID> allowed = Set.of(A);
        assertEquals("SITE_REQUIRED", code(() -> SiteScopeRules.requireChoice(allowed, null)));
        assertEquals("SITE_NOT_FOUND", code(() -> SiteScopeRules.requireChoice(allowed, B)));
        assertDoesNotThrow(() -> SiteScopeRules.requireChoice(allowed, A));
    }

    @Test
    void aUserWithAnEmptyScopeCannotListEverythingEither() {
        assertEquals("SITE_REQUIRED", code(() -> SiteScopeRules.requireChoice(Set.of(), null)));
        assertEquals("SITE_NOT_FOUND", code(() -> SiteScopeRules.requireChoice(Set.of(), A)));
    }

    @Test
    void theServiceHelperIsNullSafeAndDelegates() throws Exception {
        String s = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/SiteScopeService.java"));
        assertTrue(s.contains("if (s == null) return;\n        SiteScopeRules.requireChoice(s.allowedSites(tenantId, userId), siteId);"));
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

    private static String firstStatements(Method m) {
        StringBuilder b = new StringBuilder();
        for (String l : m.body().split("\n")) {
            if (l.isBlank() || l.trim().startsWith("//")) continue;
            b.append(l.trim()).append('\n');
            if (b.toString().split("\n").length == 2) break;
        }
        return b.toString();
    }

    @Test
    void everyMethodWithASiteIdChecksItBeforeDoingAnyWork() throws Exception {
        int checked = 0;
        for (String f : COVERED) {
            for (Method m : methods(f)) {
                boolean required = m.signature().contains("UUID siteId");
                boolean optional = m.signature().contains("required = false) UUID siteId");
                if (!required && !optional) continue;
                String want = optional ? "if (siteId != null) site(siteId);" : "site(siteId);";
                assertTrue(m.body().contains(want), f + "." + m.name() + " must call " + want);
                assertTrue(firstStatements(m).contains(want), f + "." + m.name() + ": the check must be among the first statements");
                assertEquals(1, count(m.body(), want), f + "." + m.name());
                checked++;
            }
        }
        assertEquals(18, checked, "18 endpoints take a site id in these controllers");
    }

    @Test
    void theCheckIsNeverAfterTheWork() throws Exception {
        for (String f : COVERED) {
            for (Method m : methods(f)) {
                for (String call : new String[]{"site(siteId);", "wholeCompany();", "site(req.siteId());"}) {
                    int at = m.body().indexOf(call);
                    if (at < 0) continue;
                    String before = m.body().substring(0, at);
                    assertFalse(before.contains("Service.") || before.contains("service."), f + "." + m.name() + " does work before the scope check");
                }
            }
        }
    }

    @Test
    void routeCreationChecksTheSiteInTheBody() throws Exception {
        Method create = methods("PatrolRouteController").stream().filter(x -> x.name().equals("createRoute")).findFirst().orElseThrow();
        assertTrue(create.body().contains("site(req.siteId());"));
        assertTrue(create.body().indexOf("site(req.siteId());") < create.body().indexOf("patrolRoundService.createRoute"));
    }

    @Test
    void theCameraListAndAlarmQueueAreFilteredByTheUsersSites() throws Exception {
        Method cams = methods("CameraController").stream().filter(x -> x.name().equals("getAll")).findFirst().orElseThrow();
        assertTrue(cams.body().contains("java.util.Set<UUID> allowed = allowed();"));
        assertFalse(cams.body().contains("wholeCompany();"));
        Method queue = methods("ControlRoomController").stream().filter(x -> x.name().equals("getOpenQueue")).findFirst().orElseThrow();
        assertTrue(queue.body().contains("java.util.Set<UUID> allowed = allowed();"));
        assertFalse(queue.body().contains("wholeCompany();"));
    }

    @Test
    void everyCoveredControllerHasTheOptionalCollaboratorAndAllThreeHelpers() throws Exception {
        for (String f : COVERED) {
            String c = Files.readString(Path.of(API + f + ".java"));
            assertTrue(c.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)\n    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;"), f);
            assertTrue(c.contains("SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);"), f);
            assertTrue(c.contains("SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());"), f);
        }
    }

    @Test
    void theGuardsOwnEndpointsAreNotAffected() throws Exception {
        String c = Files.readString(Path.of(API + "GuardGateAccessController.java"));
        assertFalse(c.contains("SiteScopeService"), "a guard's site comes from the open session, not from staff scope");
    }

    private static int count(String s, String what) {
        int n = 0, i = 0;
        while ((i = s.indexOf(what, i)) >= 0) { n++; i += what.length(); }
        return n;
    }
}
