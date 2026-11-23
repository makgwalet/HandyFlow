package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Date rules that carry legal weight (PSiRA expiry, firearm competency, licence expiry) are judged on the company's business day, not the server's. */
class CompanyDayChecksTest {

    private static String src(String rel) throws Exception { return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/" + rel)); }
    private static final String SVC = "application/internal/";

    @Test
    void theServicesUseTheCompanyDayAndStillWorkWhenBuiltByHand() throws Exception {
        for (String f : new String[]{"ShiftService", "ArmouryService", "ClientPortalService", "SiteAccessPdfService"}) {
            String s = src(SVC + f + ".java");
            assertTrue(s.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)\n    private za.co.handyflow.platform.shared.BusinessCalendar calendar;"), f);
            assertFalse(s.contains("private final za.co.handyflow.platform.shared.BusinessCalendar"), f + " must stay optional so existing constructors keep working");
            assertFalse(s.contains("LocalDate.now()"), f);
            assertFalse(s.contains("ZoneId.of(\"Africa/Johannesburg\")"), f);
        }
    }

    @Test
    void psiraExpiryWhenSchedulingIsJudgedOnTheCompanyDay() throws Exception {
        String s = src(SVC + "ShiftService.java");
        assertTrue(s.contains("guard.getPsiraExpiryDate().isBefore(za.co.handyflow.platform.shared.BusinessCalendar.todayOf(calendar, tenantId))"));
        assertTrue(s.contains(".withZone(za.co.handyflow.platform.shared.BusinessCalendar.zoneOf(calendar, tenantId)).format(req.startAt())"));
    }

    @Test
    void firearmIssueChecksUseTheCompanyDay() throws Exception {
        String s = src(SVC + "ArmouryService.java");
        assertTrue(s.contains("IssueChecks.psira(za.co.handyflow.platform.shared.BusinessCalendar.todayOf(calendar, tenantId),"));
        assertTrue(s.contains("strictCompetency,\n                za.co.handyflow.platform.shared.BusinessCalendar.todayOf(calendar, tenantId));"));
    }

    @Test
    void thePortalAndAccessReportShowTheCompanyDay() throws Exception {
        assertTrue(src(SVC + "ClientPortalService.java").contains("todayOf(calendar, site.getTenantId())"));
        String s = src(SVC + "SiteAccessPdfService.java");
        assertTrue(s.contains("\"Generated \" + za.co.handyflow.platform.shared.BusinessCalendar.todayOf(calendar, tenantId)"));
        assertTrue(s.contains("formatInstant(line.loggedInAt(), zone)"));
        assertTrue(s.contains(".withZone(zone)"));
    }

    @Test
    void entitiesThatHaveNoCompanyUseTheDefaultZoneNotTheServers() throws Exception {
        int uses = 0;
        for (String f : new String[]{"Guard", "Armoury", "DeclinedPrincipal", "RotationAssignment"}) {
            String s = src("domain/model/" + f + ".java");
            assertFalse(s.contains("LocalDate.now()"), f);
            uses += s.split("LocalDate\\.now\\(za\\.co\\.handyflow\\.platform\\.shared\\.BusinessTime\\.DEFAULT_ZONE\\)", -1).length - 1;
        }
        assertEquals(5, uses);
    }
}
