package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SitePermissionRulesTest {
    private static final LocalDate D = LocalDate.of(2027, 3, 10);
    private static final UUID PROG = UUID.randomUUID();

    @Test
    void creationChecks() {
        assertNull(SitePermissionRules.problem("FACILITATOR", true, true, D, null, "Placed at this site for the new intake"));
        assertEquals("Choose a role", SitePermissionRules.problem("BOSS", true, true, D, null, "Placed at this site for the new intake"));
        assertEquals("This person is inactive", SitePermissionRules.problem("ASSESSOR", false, true, D, null, "Placed at this site for the new intake"));
        assertEquals("That site is not active", SitePermissionRules.problem("ASSESSOR", true, false, D, null, "Placed at this site for the new intake"));
        assertEquals("Give the day the permission starts", SitePermissionRules.problem("ASSESSOR", true, true, null, null, "Placed at this site for the new intake"));
        assertEquals("The end date cannot be before the start date", SitePermissionRules.problem("ASSESSOR", true, true, D, D.minusDays(1), "Placed at this site for the new intake"));
        assertNotNull(SitePermissionRules.problem("ASSESSOR", true, true, D, null, "short"));
    }

    @Test
    void coverFollowsDatesAndProgramme() {
        var open = new SitePermissionRules.Permission(null, D, null, true);
        assertEquals("COVERED", SitePermissionRules.coverOn(List.of(open), PROG, D));
        assertEquals("NOT_IN_FORCE", SitePermissionRules.coverOn(List.of(open), PROG, D.minusDays(1)));
        var ended = new SitePermissionRules.Permission(null, D, D.plusDays(10), true);
        assertEquals("NOT_IN_FORCE", SitePermissionRules.coverOn(List.of(ended), PROG, D.plusDays(11)));
        var other = new SitePermissionRules.Permission(UUID.randomUUID(), D, null, true);
        assertEquals("NOT_RECORDED", SitePermissionRules.coverOn(List.of(other), PROG, D));
        assertEquals("COVERED", SitePermissionRules.coverOn(List.of(other, open), PROG, D));
        var withdrawn = new SitePermissionRules.Permission(null, D, null, false);
        assertEquals("NOT_RECORDED", SitePermissionRules.coverOn(List.of(withdrawn), PROG, D));
        assertEquals("NOT_RECORDED", SitePermissionRules.coverOn(List.of(), PROG, D));
    }

    @Test
    void wordingNeverCallsAMissingRecordAFinding() {
        assertEquals("No site permission recorded for this person at this site", SitePermissionRules.note("NOT_RECORDED"));
        assertFalse(SitePermissionRules.note("NOT_RECORDED").toLowerCase().contains("not allowed"));
    }

    @Test
    void withdrawalNeedsAReason() {
        assertNotNull(SitePermissionRules.withdrawProblem("ACTIVE", "short"));
        assertNotNull(SitePermissionRules.withdrawProblem("WITHDRAWN", "A long enough reason"));
        assertNull(SitePermissionRules.withdrawProblem("ACTIVE", "Moved to the northern branch"));
    }
}
