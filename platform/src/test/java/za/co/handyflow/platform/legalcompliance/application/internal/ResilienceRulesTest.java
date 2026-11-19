package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.domain.model.Criticality;
import za.co.handyflow.platform.legalcompliance.domain.model.EsgDirection;
import za.co.handyflow.platform.legalcompliance.domain.model.EsgFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ResilienceRulesTest {

    private static final LocalDate D = LocalDate.of(2026, 1, 15);

    @Test @DisplayName("Continuity: exercise interval follows criticality and is anchored on the last test, else on approval; no plan means nothing overdue")
    void exercises() {
        assertEquals(6, ContinuityRules.testIntervalMonths(Criticality.CRITICAL));
        assertEquals(12, ContinuityRules.testIntervalMonths(Criticality.HIGH));
        assertEquals(24, ContinuityRules.testIntervalMonths(Criticality.LOW));
        assertFalse(ContinuityRules.testOverdue(Criticality.CRITICAL, D, null, D.plusMonths(6)));
        assertTrue(ContinuityRules.testOverdue(Criticality.CRITICAL, D, null, D.plusMonths(6).plusDays(1)));
        assertTrue(ContinuityRules.testOverdue(Criticality.MEDIUM, null, D, D.plusMonths(12).plusDays(1)));
        assertFalse(ContinuityRules.testOverdue(Criticality.CRITICAL, null, null, D.plusYears(5)));
        // A recent test wins over an old approval date.
        assertFalse(ContinuityRules.testOverdue(Criticality.CRITICAL, D.plusMonths(5), D, D.plusMonths(8)));
        assertEquals(D.plusMonths(6), ContinuityRules.testDueOn(Criticality.CRITICAL, D, null));
        assertNull(ContinuityRules.testDueOn(Criticality.HIGH, null, null));
    }

    @Test @DisplayName("Continuity: impact-analysis and plan review dates; RTO comparison")
    void reviews() {
        assertFalse(ContinuityRules.biaOverdue(D, 12, D.minusYears(3), D.plusMonths(12)));
        assertTrue(ContinuityRules.biaOverdue(D, 12, D.minusYears(3), D.plusMonths(12).plusDays(1)));
        assertTrue(ContinuityRules.biaOverdue(null, 12, D, D.plusMonths(12).plusDays(1)));
        assertFalse(ContinuityRules.planReviewOverdue(D, D));
        assertTrue(ContinuityRules.planReviewOverdue(D, D.plusDays(1)));
        assertFalse(ContinuityRules.planReviewOverdue(null, D));
        assertEquals(Boolean.TRUE, ContinuityRules.rtoMet(new BigDecimal("4.00"), 4));
        assertEquals(Boolean.FALSE, ContinuityRules.rtoMet(new BigDecimal("4.01"), 4));
        assertNull(ContinuityRules.rtoMet(null, 4));
    }

    @Test @DisplayName("ESG: next period end keeps month-ends; overdue only after the grace; with no reading, one period from creation")
    void periods() {
        assertEquals(LocalDate.of(2026, 6, 30), EsgRules.nextPeriodEnd(EsgFrequency.QUARTERLY, LocalDate.of(2026, 3, 31)));
        assertEquals(LocalDate.of(2026, 2, 28), EsgRules.nextPeriodEnd(EsgFrequency.MONTHLY, LocalDate.of(2026, 1, 31)));
        assertEquals(LocalDate.of(2027, 12, 31), EsgRules.nextPeriodEnd(EsgFrequency.ANNUALLY, LocalDate.of(2026, 12, 31)));
        assertEquals(LocalDate.of(2026, 5, 15), EsgRules.nextPeriodEnd(EsgFrequency.MONTHLY, LocalDate.of(2026, 4, 15)));
        LocalDate latest = LocalDate.of(2026, 3, 31);
        LocalDate limit = LocalDate.of(2026, 6, 30).plusDays(EsgRules.GRACE_DAYS);
        assertFalse(EsgRules.readingOverdue(EsgFrequency.QUARTERLY, latest, D, limit));
        assertTrue(EsgRules.readingOverdue(EsgFrequency.QUARTERLY, latest, D, limit.plusDays(1)));
        assertFalse(EsgRules.readingOverdue(EsgFrequency.MONTHLY, null, D, D.plusMonths(1).plusDays(EsgRules.GRACE_DAYS)));
        assertTrue(EsgRules.readingOverdue(EsgFrequency.MONTHLY, null, D, D.plusMonths(1).plusDays(EsgRules.GRACE_DAYS + 1)));
    }

    @Test @DisplayName("ESG: target status respects the direction; track-only and missing values give none")
    void targets() {
        BigDecimal ten = BigDecimal.TEN;
        assertEquals(EsgRules.TargetStatus.MET, EsgRules.targetStatus(EsgDirection.LOWER_IS_BETTER, ten, new BigDecimal("10.0")));
        assertEquals(EsgRules.TargetStatus.MISSED, EsgRules.targetStatus(EsgDirection.LOWER_IS_BETTER, ten, new BigDecimal("10.1")));
        assertEquals(EsgRules.TargetStatus.MET, EsgRules.targetStatus(EsgDirection.HIGHER_IS_BETTER, ten, new BigDecimal("10")));
        assertEquals(EsgRules.TargetStatus.MISSED, EsgRules.targetStatus(EsgDirection.HIGHER_IS_BETTER, ten, new BigDecimal("9.99")));
        assertEquals(EsgRules.TargetStatus.NONE, EsgRules.targetStatus(EsgDirection.TRACK_ONLY, null, ten));
        assertEquals(EsgRules.TargetStatus.NONE, EsgRules.targetStatus(EsgDirection.LOWER_IS_BETTER, ten, null));
        assertEquals(EsgRules.TargetStatus.NONE, EsgRules.targetStatus(null, ten, ten));
    }
}
