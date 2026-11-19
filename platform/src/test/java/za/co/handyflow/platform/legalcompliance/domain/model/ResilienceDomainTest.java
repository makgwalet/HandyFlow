package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResilienceDomainTest {

    private static final TenantId T = TenantId.generate();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final UUID AUTHOR = UUID.randomUUID(), OTHER = UUID.randomUUID();
    private static final String PLAN_TEXT = "Invoke the crisis team, move operations to the secondary site and notify customers within two hours.";

    private BcProcess newProcess() { return BcProcess.create(T, "BCP-00001", "Payroll run", null, "Finance head", "Finance", Criticality.CRITICAL, 4, 1, null, 12); }
    private BcPlan newPlan() { return BcPlan.draft(T, "BCPL-00001", UUID.randomUUID(), "Payroll recovery", "v1", "Finance head", PLAN_TEXT, 12, AUTHOR, "Author"); }

    @Test @DisplayName("Process: validates objectives and owner; retired processes are closed to edits and reviews")
    void processRules() {
        assertThrows(IllegalArgumentException.class, () -> BcProcess.create(T, "R", "P", null, "O", null, Criticality.HIGH, 0, 1, null, 12), "rto");
        assertThrows(IllegalArgumentException.class, () -> BcProcess.create(T, "R", "P", null, "O", null, Criticality.HIGH, 9000, 1, null, 12), "rto max");
        assertThrows(IllegalArgumentException.class, () -> BcProcess.create(T, "R", "P", null, "O", null, Criticality.HIGH, 4, -1, null, 12), "rpo");
        assertThrows(IllegalArgumentException.class, () -> BcProcess.create(T, "R", "P", null, " ", null, Criticality.HIGH, 4, 1, null, 12), "owner");
        assertThrows(IllegalArgumentException.class, () -> BcProcess.create(T, "R", "P", null, "O", null, Criticality.HIGH, 4, 1, null, 40), "bia months");
        BcProcess p = newProcess();
        p.markBiaReviewed(TODAY, TODAY);
        assertThrows(IllegalArgumentException.class, () -> p.markBiaReviewed(TODAY.plusDays(1), TODAY), "future");
        assertThrows(IllegalArgumentException.class, () -> p.markBiaReviewed(TODAY.minusDays(1), TODAY), "backwards");
        assertThrows(IllegalArgumentException.class, () -> p.retire("short"));
        p.retire("Function outsourced to a bureau");
        assertEquals(BcProcessStatus.RETIRED, p.getStatus());
        assertThrows(IllegalStateException.class, () -> p.update("x", null, "o", null, Criticality.LOW, 1, 1, null, 12));
        assertThrows(IllegalStateException.class, () -> p.markBiaReviewed(TODAY, TODAY));
        assertThrows(IllegalStateException.class, () -> p.retire("again and again"));
    }

    @Test @DisplayName("Plan: needs real content; the author cannot approve; approval sets the review date; an approved plan is not edited in place")
    void planRules() {
        assertThrows(IllegalArgumentException.class, () -> BcPlan.draft(T, "R", UUID.randomUUID(), "T", "v1", "O", "too short", 12, AUTHOR, "A"), "content");
        assertThrows(IllegalArgumentException.class, () -> BcPlan.draft(T, "R", UUID.randomUUID(), "T", "v1", "O", PLAN_TEXT, 12, null, "A"), "author");
        BcPlan p = newPlan();
        assertThrows(IllegalStateException.class, () -> p.approve(AUTHOR, "Author", TODAY), "self-approval");
        assertThrows(IllegalStateException.class, () -> p.confirmReview(TODAY), "draft review");
        p.approve(OTHER, "Approver", TODAY);
        assertEquals(BcPlanStatus.APPROVED, p.getStatus());
        assertEquals(TODAY.plusMonths(12), p.getNextReviewDue());
        assertThrows(IllegalStateException.class, () -> p.update("t", "v2", "o", PLAN_TEXT, 12), "approved is not edited");
        assertThrows(IllegalStateException.class, () -> p.approve(OTHER, "Approver", TODAY), "twice");
        p.confirmReview(TODAY.plusMonths(11));
        assertEquals(TODAY.plusMonths(23), p.getNextReviewDue());
        p.retire("Superseded by v2");
        assertThrows(IllegalStateException.class, () -> p.confirmReview(TODAY));
    }

    @Test @DisplayName("Exercise: recovery time is required for failover, a missed RTO cannot be PASSED, and anything short of a pass needs issues and actions")
    void exercise() {
        UUID plan = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> BcTest.record(T, plan, 4, TODAY, BcTestType.FAILOVER, BcTestOutcome.PASSED, null, null, null, "Ops", OTHER, "U", TODAY), "failover needs rto");
        assertThrows(IllegalArgumentException.class, () -> BcTest.record(T, plan, 4, TODAY, BcTestType.SIMULATION, BcTestOutcome.PASSED, new BigDecimal("5"), null, null, "Ops", OTHER, "U", TODAY), "missed rto passed");
        assertThrows(IllegalArgumentException.class, () -> BcTest.record(T, plan, 4, TODAY, BcTestType.TABLETOP, BcTestOutcome.FAILED, null, "Backups would not restore", null, "Ops", OTHER, "U", TODAY), "actions");
        assertThrows(IllegalArgumentException.class, () -> BcTest.record(T, plan, 4, TODAY, BcTestType.TABLETOP, BcTestOutcome.PASSED_WITH_ISSUES, null, "short", "Fix the restore procedure", "Ops", OTHER, "U", TODAY), "issues");
        assertThrows(IllegalArgumentException.class, () -> BcTest.record(T, plan, 4, TODAY.plusDays(1), BcTestType.TABLETOP, BcTestOutcome.PASSED, null, null, null, "Ops", OTHER, "U", TODAY), "future");
        assertThrows(IllegalArgumentException.class, () -> BcTest.record(T, plan, 4, TODAY, BcTestType.TABLETOP, BcTestOutcome.PASSED, null, null, null, " ", OTHER, "U", TODAY), "who");
        BcTest ok = BcTest.record(T, plan, 4, TODAY, BcTestType.FAILOVER, BcTestOutcome.PASSED, new BigDecimal("3.5"), null, null, "Ops", OTHER, "U", TODAY);
        assertEquals(Boolean.TRUE, ok.rtoMet());
        BcTest failed = BcTest.record(T, plan, 4, TODAY, BcTestType.FAILOVER, BcTestOutcome.FAILED, new BigDecimal("9"), "Restore took far longer than planned", "Add a second backup site", "Ops", OTHER, "U", TODAY);
        assertEquals(Boolean.FALSE, failed.rtoMet());
        assertEquals(4, failed.getRtoTargetHours());
    }

    private EsgMetric metric(EsgDirection d, BigDecimal target) {
        return EsgMetric.create(T, "ESG-00001", "Scope 2 emissions", null, EsgCategory.ENVIRONMENTAL, "tCO2e", EsgFrequency.QUARTERLY, d, target, "Sustainability lead", "GRI 305-2");
    }

    @Test @DisplayName("Metric: a target is needed unless track-only (and vice versa); retiring needs a reason and closes it")
    void metric() {
        assertThrows(IllegalArgumentException.class, () -> metric(EsgDirection.LOWER_IS_BETTER, null), "needs target");
        assertThrows(IllegalArgumentException.class, () -> metric(EsgDirection.TRACK_ONLY, BigDecimal.ONE), "track-only target");
        assertThrows(IllegalArgumentException.class, () -> EsgMetric.create(T, "R", " ", null, EsgCategory.SOCIAL, "u", EsgFrequency.MONTHLY, EsgDirection.TRACK_ONLY, null, "o", null), "name");
        EsgMetric m = metric(EsgDirection.LOWER_IS_BETTER, new BigDecimal("100"));
        assertThrows(IllegalArgumentException.class, () -> m.retire("short"));
        m.retire("Replaced by a group-wide measure");
        assertThrows(IllegalStateException.class, () -> m.update("n", null, EsgCategory.SOCIAL, "u", EsgFrequency.MONTHLY, EsgDirection.TRACK_ONLY, null, "o", null));
    }

    @Test @DisplayName("Reading: period must have ended and be sensible; it copies the target in force; corrections are superseded with a reason, once")
    void reading() {
        EsgMetric m = metric(EsgDirection.LOWER_IS_BETTER, new BigDecimal("100"));
        LocalDate start = LocalDate.of(2026, 7, 1), end = LocalDate.of(2026, 9, 30);
        assertThrows(IllegalArgumentException.class, () -> EsgReading.record(T, m, start, TODAY.plusDays(1), BigDecimal.ONE, null, null, OTHER, "U", TODAY), "not ended");
        assertThrows(IllegalArgumentException.class, () -> EsgReading.record(T, m, end, start, BigDecimal.ONE, null, null, OTHER, "U", TODAY), "backwards");
        assertThrows(IllegalArgumentException.class, () -> EsgReading.record(T, m, LocalDate.of(2025, 1, 1), end, BigDecimal.ONE, null, null, OTHER, "U", TODAY), "too long");
        assertThrows(IllegalArgumentException.class, () -> EsgReading.record(T, m, start, end, null, null, null, OTHER, "U", TODAY), "value");
        EsgReading r = EsgReading.record(T, m, start, end, new BigDecimal("120"), " note ", null, OTHER, "U", TODAY);
        assertEquals(new BigDecimal("100"), r.getTargetSnapshot());
        assertEquals(EsgDirection.LOWER_IS_BETTER, r.getDirectionSnapshot());
        assertEquals("note", r.getNotes());
        assertTrue(r.current());
        // Changing the metric's target afterwards does not touch the reading.
        m.update(m.getName(), null, EsgCategory.ENVIRONMENTAL, "tCO2e", EsgFrequency.QUARTERLY, EsgDirection.LOWER_IS_BETTER, new BigDecimal("200"), "Sustainability lead", null);
        assertEquals(new BigDecimal("100"), r.getTargetSnapshot());
        assertThrows(IllegalArgumentException.class, () -> r.supersede("short", Instant.now()));
        r.supersede("Meter reading was misread", Instant.now());
        assertFalse(r.current());
        assertThrows(IllegalStateException.class, () -> r.supersede("Second correction attempt", Instant.now()));
        m.retire("No longer reported to the board");
        assertThrows(IllegalStateException.class, () -> EsgReading.record(T, m, start, end, BigDecimal.ONE, null, null, OTHER, "U", TODAY), "retired");
    }
}
