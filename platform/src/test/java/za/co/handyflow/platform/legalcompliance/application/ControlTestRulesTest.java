package za.co.handyflow.platform.legalcompliance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.ControlTestRules;
import za.co.handyflow.platform.legalcompliance.domain.model.*;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ControlTestRulesTest {

    private final LocalDate today = LocalDate.of(2026, 11, 19);

    @Test
    @DisplayName("test results map to operating effectiveness")
    void effectiveness() {
        assertEquals(OperatingEffectiveness.EFFECTIVE, ControlTestRules.effectivenessFor(TestResult.PASS));
        assertEquals(OperatingEffectiveness.PARTIALLY_EFFECTIVE, ControlTestRules.effectivenessFor(TestResult.PARTIAL));
        assertEquals(OperatingEffectiveness.INEFFECTIVE, ControlTestRules.effectivenessFor(TestResult.FAIL));
    }

    @Test
    @DisplayName("a pass is good for the control's interval; a partial or fail is retested in 30 days")
    void nextDue() {
        assertEquals(today.plusDays(180), ControlTestRules.nextDue(today, ControlFrequency.QUARTERLY, TestResult.PASS));
        assertEquals(today.plusDays(365), ControlTestRules.nextDue(today, ControlFrequency.ANNUALLY, TestResult.PASS));
        assertEquals(today.plusDays(90), ControlTestRules.nextDue(today, ControlFrequency.DAILY, TestResult.PASS));
        assertEquals(today.plusDays(30), ControlTestRules.nextDue(today, ControlFrequency.ANNUALLY, TestResult.FAIL));
        assertEquals(today.plusDays(30), ControlTestRules.nextDue(today, ControlFrequency.MONTHLY, TestResult.PARTIAL));
    }

    @Test
    @DisplayName("a pass only counts as effective while it is current: due today is still current, yesterday is stale")
    void currentlyEffective() {
        assertTrue(ControlTestRules.currentlyEffective(OperatingEffectiveness.EFFECTIVE, today, today));
        assertFalse(ControlTestRules.currentlyEffective(OperatingEffectiveness.EFFECTIVE, today.minusDays(1), today));
        assertFalse(ControlTestRules.currentlyEffective(OperatingEffectiveness.EFFECTIVE, null, today));
        assertFalse(ControlTestRules.currentlyEffective(OperatingEffectiveness.NOT_TESTED, today.plusDays(10), today));
        assertFalse(ControlTestRules.currentlyEffective(OperatingEffectiveness.PARTIALLY_EFFECTIVE, today.plusDays(10), today));
    }

    @Test
    @DisplayName("only partial and failed results raise findings; severity and deadline follow")
    void findings() {
        assertFalse(ControlTestRules.raisesFinding(TestResult.PASS));
        assertTrue(ControlTestRules.raisesFinding(TestResult.PARTIAL));
        assertEquals(FindingSeverity.HIGH, ControlTestRules.severityFor(TestResult.FAIL));
        assertEquals(FindingSeverity.MEDIUM, ControlTestRules.severityFor(TestResult.PARTIAL));
        assertThrows(IllegalArgumentException.class, () -> ControlTestRules.severityFor(TestResult.PASS));
        assertEquals(14, ControlTestRules.remediationDays(FindingSeverity.CRITICAL));
        assertEquals(30, ControlTestRules.remediationDays(FindingSeverity.HIGH));
        assertEquals(60, ControlTestRules.remediationDays(FindingSeverity.MEDIUM));
        assertEquals(90, ControlTestRules.remediationDays(FindingSeverity.LOW));
    }
}
