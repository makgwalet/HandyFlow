package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApplicabilityAssessmentTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID assessor = UUID.randomUUID();

    private ApplicabilityAssessment make(ApplicabilityDecision d) {
        return ApplicabilityAssessment.create(tenant, UUID.randomUUID(), UUID.randomUUID(), d, "Because we process personal information",
                ApplicabilitySuggestion.NEEDS_REVIEW, List.of("reason"), assessor, "Assessor");
    }

    @Test
    @DisplayName("APPLICABLE is approved immediately — adding a duty needs no second approver")
    void applicableAutoApproved() {
        ApplicabilityAssessment a = make(ApplicabilityDecision.APPLICABLE);
        assertEquals(AssessmentStatus.APPROVED, a.getStatus());
        assertTrue(a.isInForce());
        assertEquals(assessor, a.getDecidedBy());
    }

    @Test
    @DisplayName("NOT_APPLICABLE waits for approval and is not in force until then")
    void notApplicablePending() {
        ApplicabilityAssessment a = make(ApplicabilityDecision.NOT_APPLICABLE);
        assertEquals(AssessmentStatus.PENDING_APPROVAL, a.getStatus());
        assertFalse(a.isInForce());
        a.approve(UUID.randomUUID(), "Approver", "Agreed");
        assertEquals(AssessmentStatus.APPROVED, a.getStatus());
        assertEquals("Approver", a.getDecidedByName());
        assertNotNull(a.getDecidedAt());
    }

    @Test
    @DisplayName("a rejected assessment keeps the rejection reason and cannot then be approved")
    void rejectKeepsReason() {
        ApplicabilityAssessment a = make(ApplicabilityDecision.NOT_APPLICABLE);
        a.reject(UUID.randomUUID(), "Approver", "Threshold actually exceeded");
        assertEquals(AssessmentStatus.REJECTED, a.getStatus());
        assertEquals("Threshold actually exceeded", a.getDecisionNote());
        assertThrows(IllegalStateException.class, () -> a.approve(UUID.randomUUID(), "x", null));
    }

    @Test
    @DisplayName("only an APPROVED assessment can be superseded, and it keeps its record")
    void supersede() {
        ApplicabilityAssessment pending = make(ApplicabilityDecision.NOT_APPLICABLE);
        assertThrows(IllegalStateException.class, pending::supersede);

        ApplicabilityAssessment a = make(ApplicabilityDecision.APPLICABLE);
        a.supersede();
        assertEquals(AssessmentStatus.SUPERSEDED, a.getStatus());
        assertNotNull(a.getSupersededAt());
        assertEquals("Because we process personal information", a.getJustification());   // history intact
        assertThrows(IllegalStateException.class, a::supersede);
    }

    @Test
    @DisplayName("the system suggestion and its reasons are frozen on the record")
    void suggestionFrozen() {
        ApplicabilityAssessment a = make(ApplicabilityDecision.APPLICABLE);
        assertEquals(ApplicabilitySuggestion.NEEDS_REVIEW, a.getSuggestion());
        assertEquals(List.of("reason"), a.getSuggestionReasons());
    }

    @Test
    @DisplayName("an obligation from a requirement needs an owner and a date, and keeps its traceability")
    void obligationFromRequirement() {
        UUID req = UUID.randomUUID(), assess = UUID.randomUUID(), ver = UUID.randomUUID(), owner = UUID.randomUUID();
        RegulatoryObligation o = RegulatoryObligation.createFromRequirement(tenant, "Register", ObligationCategory.POPIA, "ref", "desc",
                owner, "Owner", LocalDate.now().plusDays(30), RecurrenceInterval.ANNUALLY, req, assess, ver, "Legal", "Certificate", UUID.randomUUID());
        assertEquals(req, o.getRequirementId());
        assertEquals(assess, o.getAssessmentId());
        assertEquals(ver, o.getSourceVersionId());
        assertEquals("Legal", o.getDepartmentName());
        assertEquals(ObligationStatus.COMPLIANT, o.getStatus());

        assertThrows(IllegalArgumentException.class, () -> RegulatoryObligation.createFromRequirement(tenant, "t", ObligationCategory.POPIA,
                "r", "d", null, null, LocalDate.now(), RecurrenceInterval.ONCE, req, assess, ver, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> RegulatoryObligation.createFromRequirement(tenant, "t", ObligationCategory.POPIA,
                "r", "d", owner, "Owner", null, RecurrenceInterval.ONCE, req, assess, ver, null, null, null));
    }

    @Test
    @DisplayName("hand-entered obligations stay untraced and unchanged")
    void handEnteredUnchanged() {
        RegulatoryObligation o = RegulatoryObligation.create(tenant, "Manual", ObligationCategory.OTHER, null, null, null, null,
                LocalDate.now().plusDays(10), RecurrenceInterval.ONCE, null);
        assertNull(o.getRequirementId());
        assertNull(o.getAssessmentId());
    }
}
