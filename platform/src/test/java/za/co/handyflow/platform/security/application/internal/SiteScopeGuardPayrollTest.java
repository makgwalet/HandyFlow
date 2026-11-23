package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SiteScopeGuardPayrollTest {

    private static final String API = "src/main/java/za/co/handyflow/platform/security/api/";

    private record Method(String name, String body) {}

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
            StringBuilder body = new StringBuilder();
            for (int i = j + 1; i < end; i++) body.append(lines[i]).append('\n');
            Matcher m = Pattern.compile("(\\w+)\\(").matcher(lines[pub]);
            m.find();
            out.add(new Method(m.group(1), body.toString()));
        }
        return out;
    }

    private static String firstCode(Method m) {
        for (String l : m.body().split("\n")) {
            String t = l.trim();
            if (t.isEmpty() || t.startsWith("//") || t.contains("requireModule(\"security\")")) continue;
            return t;
        }
        return "";
    }

    @Test
    void everyPayrollEndpointIsForTheWholeCompanyOnly() throws Exception {
        List<Method> ms = methods("PayrollController");
        assertEquals(11, ms.size());
        for (Method m : ms) assertEquals("wholeCompany();", firstCode(m), "PayrollController." + m.name());
    }

    @Test
    void guardChangesAndSensitiveReadsAreForTheWholeCompanyOnly() throws Exception {
        List<Method> ms = methods("GuardController");
        Set<String> whole = Set.of("getOverview", "createGuard", "updateGuard", "updateStatus", "deleteGuard", "updatePhoto", "reveal", "removeFace", "updateBankDetails");
        int seen = 0;
        for (Method m : ms) {
            if (whole.contains(m.name())) {
                assertEquals("wholeCompany();", firstCode(m), "GuardController." + m.name());
                seen++;
            }
        }
        assertEquals(whole.size(), seen);
    }

    @Test
    void theGuardListAndProfileStayOpenForSchedulingAndSayWhy() throws Exception {
        int seen = 0;
        for (Method m : methods("GuardController")) {
            if (Set.of("getGuards", "directory", "getGuard", "faceStatus").contains(m.name())) {
                assertFalse(m.body().contains("wholeCompany();"), m.name() + " is how a limited manager picks a guard to schedule");
                seen++;
            }
        }
        assertEquals(4, seen);
    }

    @Test
    void everyControllerHasTheHelpersItUses() throws Exception {
        for (String f : new String[]{"PayrollController", "GuardController"}) {
            String c = Files.readString(Path.of(API + f + ".java"));
            assertTrue(c.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)\n    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;"), f);
            assertTrue(c.contains("SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());"), f);
        }
    }

    @Test
    void onlyAnUnrestrictedUserCanChangeAScopeAndItHappensBeforeAnyWrite() throws Exception {
        String c = Files.readString(Path.of(API + "SiteScopeController.java"));
        int put = c.indexOf("@PutMapping");
        String body = c.substring(put, c.indexOf("\n    }\n", put));
        int check = body.indexOf("SiteScopeService.checkUnrestricted(service, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());");
        int write = body.indexOf("service.replace(");
        assertTrue(check > 0 && write > check, "the check must come before the write");
        assertTrue(body.indexOf("featureGuard.requireModule(\"security\");") < check);
        // reading someone else's scope is not a change
        int get = c.indexOf("@GetMapping(\"/api/v1/security/users");
        assertFalse(c.substring(get, put).contains("checkUnrestricted"));
    }

    @Test
    void dispatchesAndPatrolRoundsAreNowChecked() throws Exception {
        for (Method m : methods("ControlRoomController")) {
            if (m.name().equals("recordArrival") || m.name().equals("resolveDispatch")) {
                assertTrue(m.body().contains("child(SiteScopeService.Kind.DISPATCH, id);"), m.name());
            }
        }
        for (Method m : methods("PatrolController")) {
            if (m.name().equals("detail") || m.name().equals("acknowledge")) {
                assertTrue(m.body().contains("child(SiteScopeService.Kind.PATROL_ROUND, id);"), m.name());
            }
        }
    }
}
