package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ControlTestingDomainTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID tester = UUID.randomUUID(), reviewer = UUID.randomUUID(), owner = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 19);
    private final List<Map<String, Object>> evidence = List.of(Map.of("id", UUID.randomUUID().toString(), "fileName", "access-review.pdf"));

    private Control activeControl() {
        Control c = Control.create(tenant, "CTL-00001", "Quarterly access review", "Only authorised access", "Review the register", ControlType.DETECTIVE,
                ControlNature.MANUAL, ControlFrequency.QUARTERLY, owner, "Owner", "Signed review", owner);
        c.activate();
        return c;
    }

    private ControlTest planned(Control c) {
        return ControlTest.schedule(tenant, "CTST-00001", c, ControlTestType.SCHEDULED, today, null, null, null, owner);
    }

    private ControlTest submitted(TestResult result) {
        ControlTest t = planned(activeControl());
        t.start(tester, "Tester");
        t.submit(result, 25, "25 of 120 joiners", "Sampled 25 leavers; all access removed within a day", evidence);
        return t;
    }

    @Test
    @DisplayName("only an active control can be tested; the test snapshots the control's procedure and design rating")
    void scheduleRules() {
        Control draft = Control.create(tenant, "CTL-2", "Draft", null, null, ControlType.PREVENTIVE, ControlNature.MANUAL, ControlFrequency.DAILY, owner, "O", null, owner);
        assertThrows(IllegalStateException.class, () -> ControlTest.schedule(tenant, "CTST-2", draft, ControlTestType.SCHEDULED, today, null, null, null, owner));
        ControlTest t = planned(activeControl());
        assertEquals("Review the register", t.getProcedureText());
        assertEquals("Signed review", t.getExpectedEvidence());
        assertEquals(ControlTestStatus.PLANNED, t.getStatus());
    }

    @Test
    @DisplayName("a retest must say which test it re-tests")
    void retestNeedsOriginal() {
        assertThrows(IllegalArgumentException.class, () -> ControlTest.schedule(tenant, "CTST-3", activeControl(), ControlTestType.RETEST, today, null, null, null, owner));
        assertDoesNotThrow(() -> ControlTest.schedule(tenant, "CTST-3", activeControl(), ControlTestType.RETEST, today, null, null, UUID.randomUUID(), owner));
    }

    @Test
    @DisplayName("an assigned reviewer cannot start the test as the tester")
    void reviewerCannotBeTester() {
        ControlTest t = ControlTest.schedule(tenant, "CTST-4", activeControl(), ControlTestType.AD_HOC, today, reviewer, "Reviewer", null, owner);
        assertThrows(IllegalStateException.class, () -> t.start(reviewer, "Reviewer"));
        t.start(tester, "Tester");
        assertEquals(ControlTestStatus.IN_PROGRESS, t.getStatus());
    }

    @Test
    @DisplayName("submitting needs a sample, notes of 15+ characters and at least one evidence file — even for a pass")
    void submitRules() {
        ControlTest t = planned(activeControl());
        t.start(tester, "Tester");
        assertThrows(IllegalArgumentException.class, () -> t.submit(TestResult.PASS, 25, null, "Everything was fine here", List.of()));
        assertThrows(IllegalArgumentException.class, () -> t.submit(TestResult.PASS, null, null, "Everything was fine here", evidence));
        assertThrows(IllegalArgumentException.class, () -> t.submit(TestResult.PASS, 25, null, "ok", evidence));
        assertThrows(IllegalArgumentException.class, () -> t.submit(null, 25, null, "Everything was fine here", evidence));
        assertEquals(ControlTestStatus.IN_PROGRESS, t.getStatus());
        t.submit(TestResult.PASS, 25, null, "Everything was fine here", evidence);
        assertEquals(ControlTestStatus.SUBMITTED, t.getStatus());
        assertEquals(1, t.getEvidenceSnapshot().size());
    }

    @Test
    @DisplayName("the tester cannot review their own test, and a test must be submitted before review")
    void segregation() {
        ControlTest t = submitted(TestResult.PASS);
        assertThrows(IllegalStateException.class, () -> t.approve(tester, "Tester", null));
        assertThrows(IllegalStateException.class, () -> t.returnForRework(tester, "Tester", "Please fix the sample list"));
        assertEquals(ControlTestStatus.SUBMITTED, t.getStatus());
        t.approve(reviewer, "Reviewer", "Agreed");
        assertEquals(ControlTestStatus.COMPLETED, t.getStatus());
        assertEquals("Reviewer", t.getReviewedByName());
        ControlTest early = planned(activeControl());
        assertThrows(IllegalStateException.class, () -> early.approve(reviewer, "Reviewer", null));
    }

    @Test
    @DisplayName("returning a test clears the submission, counts the return and needs a reason")
    void returned() {
        ControlTest t = submitted(TestResult.FAIL);
        assertThrows(IllegalArgumentException.class, () -> t.returnForRework(reviewer, "Reviewer", "no"));
        t.returnForRework(reviewer, "Reviewer", "Sample does not cover contractors");
        assertEquals(ControlTestStatus.IN_PROGRESS, t.getStatus());
        assertNull(t.getResult());
        assertNull(t.getSubmittedAt());
        assertEquals(1, t.getReturnedCount());
    }

    @Test
    @DisplayName("cancel needs a reason and is only possible before submission")
    void cancel() {
        ControlTest t = planned(activeControl());
        assertThrows(IllegalArgumentException.class, () -> t.cancel("no"));
        t.cancel("Control was redesigned before testing");
        assertEquals(ControlTestStatus.CANCELLED, t.getStatus());
        assertFalse(t.isOpen());
        assertThrows(IllegalStateException.class, () -> submitted(TestResult.PASS).cancel("Too late to cancel this one"));
    }

    @Test
    @DisplayName("a control's effectiveness changes only through recordTestOutcome, which refuses NOT_TESTED and inactive controls")
    void controlOutcome() {
        Control c = activeControl();
        assertEquals(OperatingEffectiveness.NOT_TESTED, c.getOperatingEffectiveness());
        assertThrows(IllegalArgumentException.class, () -> c.recordTestOutcome(OperatingEffectiveness.NOT_TESTED, Instant.now(), today));
        c.recordTestOutcome(OperatingEffectiveness.EFFECTIVE, Instant.now(), today.plusDays(180));
        assertEquals(OperatingEffectiveness.EFFECTIVE, c.getOperatingEffectiveness());
        assertEquals(today.plusDays(180), c.getNextTestDue());
        c.retire("Replaced by an automated control");
        assertThrows(IllegalStateException.class, () -> c.recordTestOutcome(OperatingEffectiveness.EFFECTIVE, Instant.now(), today));
    }

    @Test
    @DisplayName("a material change to a tested control resets its effectiveness and next test date")
    void materialChangeResetsEffectiveness() {
        Control c = activeControl();
        c.recordTestOutcome(OperatingEffectiveness.EFFECTIVE, Instant.now(), today.plusDays(180));
        assertTrue(c.update("Quarterly access review", c.getObjective(), c.getProcedureText(), c.getControlType(), c.getNature(), ControlFrequency.MONTHLY, owner, "Owner", c.getExpectedEvidence()));
        assertEquals(OperatingEffectiveness.NOT_TESTED, c.getOperatingEffectiveness());
        assertNull(c.getNextTestDue());
        c.recordTestOutcome(OperatingEffectiveness.EFFECTIVE, Instant.now(), today.plusDays(90));
        assertFalse(c.update("Renamed control", c.getObjective(), c.getProcedureText(), c.getControlType(), c.getNature(), c.getFrequency(), owner, "New owner", c.getExpectedEvidence()));
        assertEquals(OperatingEffectiveness.EFFECTIVE, c.getOperatingEffectiveness());
    }

    // ── findings ──

    private Finding finding() {
        return Finding.fromControlTest(tenant, "FND-00001", UUID.randomUUID(), UUID.randomUUID(), "Control failed", "Leavers kept access", FindingSeverity.HIGH, owner, "Owner", today.plusDays(30), tester, "Tester");
    }

    private Finding resolved(UUID resolver) {
        Finding f = finding();
        f.startRemediation("Automate leaver access removal", owner, "Owner", today.plusDays(30));
        f.resolve(resolver, "Resolver", "Automated the removal and cleaned up access");
        return f;
    }

    @Test
    @DisplayName("a finding moves OPEN → IN_REMEDIATION → RESOLVED, each step with its required text")
    void findingLifecycle() {
        Finding f = finding();
        assertEquals(FindingStatus.OPEN, f.getStatus());
        assertThrows(IllegalStateException.class, () -> f.resolve(owner, "O", "Done and dusted already"));
        assertThrows(IllegalArgumentException.class, () -> f.startRemediation("short", owner, "Owner", today));
        f.startRemediation("Automate leaver access removal", owner, "Owner", today.plusDays(30));
        assertEquals(FindingStatus.IN_REMEDIATION, f.getStatus());
        assertNotNull(f.getRemediationStartedAt());
        assertThrows(IllegalArgumentException.class, () -> f.resolve(owner, "O", "short"));
        f.resolve(owner, "Owner", "Automated the removal and cleaned up access");
        assertEquals(FindingStatus.RESOLVED, f.getStatus());
    }

    @Test
    @DisplayName("the person who resolved a finding cannot verify, reject or close it")
    void findingSegregation() {
        Finding f = resolved(owner);
        UUID retest = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> f.close(owner, "Owner", "Verified against the retest", retest));
        assertThrows(IllegalStateException.class, () -> f.rejectResolution(owner, "Owner", "Not good enough yet"));
        assertEquals(FindingStatus.RESOLVED, f.getStatus());
    }

    @Test
    @DisplayName("closing needs a retest and a note; a rejected resolution returns to remediation and is counted")
    void findingClosure() {
        Finding f = resolved(owner);
        UUID verifier = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> f.close(verifier, "Verifier", "Verified against the retest", null));
        assertThrows(IllegalArgumentException.class, () -> f.close(verifier, "Verifier", "ok", UUID.randomUUID()));
        f.rejectResolution(verifier, "Verifier", "Contractors still keep access");
        assertEquals(FindingStatus.IN_REMEDIATION, f.getStatus());
        assertEquals(1, f.getReopenedCount());
        f.resolve(owner, "Owner", "Extended the process to contractors");
        UUID retest = UUID.randomUUID();
        f.close(verifier, "Verifier", "Retest passed on a fresh sample", retest);
        assertEquals(FindingStatus.CLOSED, f.getStatus());
        assertEquals(retest, f.getClosingTestId());
        assertFalse(f.isOpen());
        assertFalse(f.isOverdue(today.plusYears(1)));
    }

    @Test
    @DisplayName("an open finding is overdue the day after its due date")
    void overdue() {
        Finding f = finding();
        assertFalse(f.isOverdue(today.plusDays(30)));
        assertTrue(f.isOverdue(today.plusDays(31)));
    }
}
