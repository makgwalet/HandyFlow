package za.co.handyflow.platform.security.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** The staff-path session endpoints must each need a permission; the guard app uses /api/v1/guard/** instead. */
class DeviceSessionControllerAccessTest {

    private static String src() throws Exception {
        for (String r : new String[]{"src/main", "platform/src/main", "work/src/main"}) {
            Path p = Path.of(r, "java/za/co/handyflow/platform/security/api/DeviceSessionController.java");
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing DeviceSessionController"); return "";
    }

    private static final Pattern MAPPING = Pattern.compile("(?m)^    @(Get|Post|Put|Delete|Patch)Mapping(\\(\"([^\"]*)\"\\))?\\s*$");

    /** mapping line -> the annotation lines that follow it up to the method signature. */
    private static List<String[]> endpoints(String s) {
        List<String[]> out = new ArrayList<>();
        Matcher m = MAPPING.matcher(s);
        while (m.find()) {
            int end = s.indexOf("\n    public ", m.end());
            out.add(new String[]{m.group(1) + " " + (m.group(3) == null ? "" : m.group(3)), s.substring(m.end(), end)});
        }
        return out;
    }

    @Test
    void everyEndpointNeedsAStaffPermission() throws Exception {
        List<String[]> eps = endpoints(src());
        assertEquals(9, eps.size(), "endpoints found: " + eps.stream().map(e -> e[0]).toList());
        for (String[] e : eps) assertTrue(e[1].contains("@PreAuthorize(\"hasAuthority('SECURITY_"), "no permission on " + e[0]);
    }

    @Test
    void readingNeedsReadAndEveryChangeNeedsManage() throws Exception {
        for (String[] e : endpoints(src())) {
            if (e[0].startsWith("Get")) assertTrue(e[1].contains("'SECURITY_READ'") || e[1].contains("'SECURITY_MANAGE'"), e[0]);
            else assertTrue(e[1].contains("'SECURITY_MANAGE'"), "change without SECURITY_MANAGE: " + e[0]);
        }
    }

    @Test
    void noEndpointIsOpenToAnyAuthenticatedUserAgain() throws Exception {
        String s = src();
        assertFalse(s.contains("permitAll"));
        assertFalse(s.contains("isAuthenticated()"));
        assertTrue(s.contains("@RequestMapping(\"/api/v1/security/sessions\")"));
    }
}
