package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditRulesTest {

    private static final LocalDate D = LocalDate.of(2027, 3, 10);

    @Test
    void parsesOneItemPerLine() {
        assertEquals(java.util.List.of("Is the register kept?", "Are files locked?"), AuditRules.parseItems("  Is the register kept?\n\n Are files locked? \r\n"));
        assertTrue(AuditRules.parseItems(null).isEmpty());
    }

    @Test
    void templateChecks() {
        assertNull(AuditRules.templateProblem("Site check", "SITE", "Is the register kept?\nAre files locked?"));
        assertEquals("Give the checklist a name", AuditRules.templateProblem("A", "SITE", "x1"));
        assertEquals("Choose what the checklist is for", AuditRules.templateProblem("Site check", "BOGUS", "x1"));
        assertEquals("Add at least one item, one per line", AuditRules.itemsProblem(" \n "));
        assertEquals("Each item needs at least two characters", AuditRules.itemsProblem("ok item\nx"));
        assertEquals("Two items are the same", AuditRules.itemsProblem("Same thing\nsame THING"));
    }

    @Test
    void setupChecks() {
        assertNull(AuditRules.setupProblem("Q1 site audit", "SITE", "SITE", true, D));
        assertNull(AuditRules.setupProblem("Q1 site audit", "GENERAL", "COHORT", true, D));
        assertEquals("Choose the site being audited", AuditRules.setupProblem("Q1", "SITE", "SITE", false, D));
        assertEquals("A general audit is not about one site, cohort or programme", AuditRules.setupProblem("Q1", "GENERAL", "GENERAL", true, D));
        assertEquals("That checklist is for a site audit", AuditRules.setupProblem("Q1", "SITE", "COHORT", true, D));
        assertEquals("Give the planned date", AuditRules.setupProblem("Q1", "GENERAL", "GENERAL", false, null));
    }

    @Test
    void answersNeedWordsUnlessNoIssueSeen() {
        assertNull(AuditRules.answerProblem("NO_ISSUE", null));
        assertNull(AuditRules.answerProblem("ISSUE", "The register was not signed on two days"));
        assertEquals("Say what you saw, in at least 10 characters", AuditRules.answerProblem("NOT_APPLICABLE", "short"));
        assertEquals("Choose an outcome", AuditRules.answerProblem("COMPLIANT", "whatever it was"));
    }

    @Test
    void completionNeedsEveryItemAnsweredEveryIssueActedOnAndASummary() {
        assertEquals("2 item(s) are not answered yet", AuditRules.completeProblem(2, 0, "A summary of the audit"));
        assertEquals("1 issue(s) have no corrective action yet", AuditRules.completeProblem(0, 1, "A summary of the audit"));
        assertEquals("Write a summary of at least 10 characters", AuditRules.completeProblem(0, 0, "short"));
        assertNull(AuditRules.completeProblem(0, 0, "A summary of the audit"));
    }

    @Test
    void secondPersonOrAWrittenReason() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertNull(AuditRules.secondPersonProblem(a, b, false, null, "complete this audit"));
        assertNull(AuditRules.secondPersonProblem(null, a, false, null, "complete this audit"));
        assertTrue(AuditRules.secondPersonProblem(a, a, false, null, "complete this audit").startsWith("A second person should complete this audit"));
        assertTrue(AuditRules.secondPersonProblem(a, a, true, "short", "complete this audit").contains("at least 10"));
        assertNull(AuditRules.secondPersonProblem(a, a, true, "Only one qualified person on site", "complete this audit"));
    }

    @Test
    void actionChecks() {
        assertNull(AuditRules.actionProblem("Sign the register daily", "Thandi", D, D));
        assertEquals("Say what has to be done", AuditRules.actionProblem("Fix", "Thandi", D, D));
        assertEquals("Say who owns the action", AuditRules.actionProblem("Sign the register daily", "T", D, D));
        assertEquals("Give the day it is due", AuditRules.actionProblem("Sign the register daily", "Thandi", null, D));
        assertEquals("The due date cannot be in the past", AuditRules.actionProblem("Sign the register daily", "Thandi", D.minusDays(1), D));
    }

    @Test
    void overdueIsOnlyForOpenActionsPastTheirDay() {
        assertTrue(AuditRules.overdue("OPEN", D.minusDays(1), D));
        assertFalse(AuditRules.overdue("OPEN", D, D));
        assertFalse(AuditRules.overdue("DONE", D.minusDays(30), D));
    }
}
