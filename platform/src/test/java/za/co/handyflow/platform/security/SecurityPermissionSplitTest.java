package za.co.handyflow.platform.security;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.SecurityRoleTemplates;

/** Source-scanning checks for the payroll/integrations permission split and the role templates. Needs no database. */
class SecurityPermissionSplitTest {

    private static final Path MAIN = Path.of("src/main");
    private static final Pattern MAPPING = Pattern.compile("@(Get|Post|Put|Delete)Mapping\\(?\\s*(?:\"([^\"]*)\")?");
    private static final Pattern AUTH = Pattern.compile("@PreAuthorize\\(\"([^\"]*)\"\\)");

    private static List<String[]> endpoints(String file) throws IOException {
        List<String[]> out = new ArrayList<>();
        String method = null, path = null;
        for (String line : Files.readAllLines(MAIN.resolve("java/za/co/handyflow/platform/security/api/" + file))) {
            Matcher m = MAPPING.matcher(line);
            if (m.find()) { method = m.group(1); path = m.group(2) == null ? "" : m.group(2); }
            Matcher a = AUTH.matcher(line);
            if (a.find() && method != null) { out.add(new String[]{method, path, a.group(1)}); method = null; }
        }
        return out;
    }

    @Test void payrollWritesNeedPayrollPermissionsNotManage() throws IOException {
        var eps = endpoints("PayrollController.java");
        assertFalse(eps.isEmpty());
        for (String[] e : eps) {
            assertFalse(e[2].contains("SECURITY_MANAGE") || e[2].contains("SECURITY_READ'"), e[0] + " " + e[1] + " " + e[2]);
            if (e[1].endsWith("/approve")) assertEquals("hasAuthority('SECURITY_PAYROLL_APPROVE')", e[2]);
            if (e[1].endsWith("/mark-paid")) assertEquals("hasAuthority('SECURITY_PAYROLL_PAY')", e[2]);
            if (e[0].equals("Get")) assertEquals("hasAuthority('SECURITY_PAYROLL_READ')", e[2], e[1]);
        }
    }

    @Test void integrationsWritesNeedIntegrationsPermission() throws IOException {
        for (String[] e : endpoints("PublicApiController.java"))
            if (!e[0].equals("Get")) assertTrue(e[2].contains("SECURITY_INTEGRATIONS_MANAGE"), e[0] + " " + e[1] + " " + e[2]);
    }

    @Test void noTemplateHasAConflict() {
        for (var t : SecurityRoleTemplates.list())
            assertEquals(List.of(), SecurityRoleTemplates.conflictsIn(t.permissions()), t.key());
    }

    @Test void conflictDetection() {
        assertEquals(1, SecurityRoleTemplates.conflictsIn(Set.of("SECURITY_PAYROLL_APPROVE", "SECURITY_PAYROLL_PAY")).size());
        assertEquals(0, SecurityRoleTemplates.conflictsIn(Set.of("SECURITY_PAYROLL_READ", "SECURITY_PAYROLL_PAY")).size());
    }

    @Test void everyTemplatePermissionIsInTheCatalogue() throws IOException {
        StringBuilder sql = new StringBuilder();
        try (Stream<Path> files = Files.walk(MAIN.resolve("resources/db/migration"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".sql")).toList()) sql.append(Files.readString(f));
        }
        for (var t : SecurityRoleTemplates.list())
            for (String p : t.permissions())
                assertTrue(sql.toString().contains("'" + p + "'"), t.key() + " uses unknown permission " + p);
    }

    @Test void guardTemplatesAreTheOnlyKeysAndAreUnique() {
        assertEquals(SecurityRoleTemplates.list().size(), SecurityRoleTemplates.all().size());
    }
}
