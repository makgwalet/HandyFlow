package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Reads the source to check the pieces that need Spring or the database to run are joined up. */
class PayPremiumWiringTest {

    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";

    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }

    @Test void payrollPaysThePremiumPartsOnlyWhenTheServiceIsThereAndSwitchedOn() throws Exception {
        String s = read("application/internal/SecurityPayrollService.java");
        assertTrue(s.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)\n    private PayPremiumService premiums;"), "optional, so a hand-built service still works");
        assertTrue(s.contains("premiums == null ? null : premiums.context(tenantId)"));
        assertTrue(s.contains("premium == null ? null\n                    : PayPremiumRules.lines("));
        assertTrue(s.contains("zone, standardHours, rateCents, premium.settings(), premium.holidays()"), "days are cut in the company's zone");
        assertTrue(s.contains("if (parts != null) {\n                addPremiumLines("));
        int premiumPath = s.indexOf("addPremiumLines(items"), legacy = s.indexOf("PayrollRules.split(shift.getStartAt()");
        assertTrue(premiumPath > 0 && legacy > premiumPath, "the ordinary split stays as the path for every other shift");
        assertTrue(s.contains("PayrollLineItem.premium("));
    }

    @Test void thePremiumContextIsReadOncePerApprovalNotPerShift() throws Exception {
        String s = read("application/internal/SecurityPayrollService.java");
        int ctx = s.indexOf("premiums.context(tenantId)"), loop = s.indexOf("for (Shift shift : completedShifts)");
        assertTrue(ctx > 0 && ctx < loop);
        assertEquals(s.indexOf("premiums.context("), s.lastIndexOf("premiums.context("));
    }

    @Test void thePayStatementReadsPremiumLinesAtTheirOwnRate() throws Exception {
        String s = read("application/internal/SecurityGuardPayStatementPdfService.java");
        assertTrue(s.contains("PayPremiumRules.hoursInOvertimeColumn(lineType)"));
        assertTrue(s.contains("PayPremiumRules.paidAtLineRate(lineType)"));
        assertTrue(s.contains("PayPremiumRules.label(lineType)"));
        assertFalse(s.contains("equals(\"OVERTIME\")"), "no leftover overtime-only test");
    }

    @Test void theLineItemHasTheTwoNewTypesAndAFactory() throws Exception {
        String s = read("domain/model/PayrollLineItem.java");
        assertTrue(s.contains("public enum LineType { REGULAR, OVERTIME, ALLOWANCE, DEDUCTION, SUNDAY, PUBLIC_HOLIDAY }"));
        assertTrue(s.contains("public static PayrollLineItem premium("));
        assertTrue(s.contains("li.hoursWorked       = hours;") && s.contains("li.overtimeHours     = BigDecimal.ZERO;"), "so hour totals stay right");
        assertTrue(s.contains("li.overtimeRateCents = premiumRateCents;"));
        assertTrue(s.contains("length = 20"), "PUBLIC_HOLIDAY (14 characters) fits the column");
    }

    @Test void theControllerReadsNeedReadAndWritesNeedManageAndBothStayOffLimitedUsers() throws Exception {
        String s = read("api/PayPremiumController.java");
        for (String[] m : new String[][]{{"@GetMapping\n", "SECURITY_READ"}, {"@PutMapping\n", "SECURITY_MANAGE"}, {"@GetMapping(\"/holidays\")", "SECURITY_READ"},
                {"@PostMapping(\"/holidays\")", "SECURITY_MANAGE"}, {"@DeleteMapping(\"/holidays\")", "SECURITY_MANAGE"}}) {
            int at = s.indexOf(m[0]);
            assertTrue(at > 0, m[0]);
            String next = s.substring(at, s.indexOf("public ", at));
            assertTrue(next.contains("hasAuthority('" + m[1] + "')"), m[0]);
        }
        assertEquals(5, s.split("SiteScopeService.checkUnrestricted\\(scope, t, TenantContext.getCurrentUserId\\(\\)\\)", -1).length - 1, "every endpoint is whole-company only");
        assertTrue(s.contains("@RequestMapping(\"/api/v1/security/payroll/premiums\")"));
    }

    @Test void theMigrationAllowsTheNewLineTypesAndKeepsTheOldOnes() throws Exception {
        String s = Files.readString(Path.of("src/main/resources/db/migration/V865__security_pay_premiums.sql"));
        for (String t : new String[]{"REGULAR", "OVERTIME", "ALLOWANCE", "DEDUCTION", "SUNDAY", "PUBLIC_HOLIDAY"}) assertTrue(s.contains("'" + t + "'"), t);
        assertTrue(s.contains("sunday_multiplier") && s.contains("holiday_multiplier") && s.contains("BETWEEN 1.50 AND 4.00"));
        assertTrue(s.contains("enabled            BOOLEAN       NOT NULL DEFAULT FALSE"), "off until a company switches it on");
    }
}
