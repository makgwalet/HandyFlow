package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RegulatoryChangeDomainTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID user = UUID.randomUUID();

    private RegulatoryImpact impact() {
        return RegulatoryImpact.create(tenant, UUID.randomUUID(), ImpactType.CHANGED, UUID.randomUUID(), UUID.randomUUID(), "s9", "Lawful processing",
                "Requirement text changed", UUID.randomUUID(), UUID.randomUUID(), LocalDate.now().plusDays(30));
    }

    @Test
    @DisplayName("a new impact is PENDING and resolving records who, when and how")
    void resolve() {
        RegulatoryImpact i = impact();
        assertTrue(i.isPending());
        i.resolve(ImpactOutcome.REASSESSED, "Still applies after review", user, "Reviewer");
        assertFalse(i.isPending());
        assertEquals(ImpactStatus.RESOLVED, i.getStatus());
        assertEquals(ImpactOutcome.REASSESSED, i.getOutcome());
        assertEquals("Reviewer", i.getResolvedByName());
        assertNotNull(i.getResolvedAt());
    }

    @Test
    @DisplayName("an impact cannot be resolved twice")
    void resolveTwice() {
        RegulatoryImpact i = impact();
        i.resolve(ImpactOutcome.ACKNOWLEDGED, null, user, "R");
        assertThrows(IllegalStateException.class, () -> i.resolve(ImpactOutcome.ACKNOWLEDGED, null, user, "R"));
    }

    @Test
    @DisplayName("a change starts OPEN and close() is idempotent")
    void changeClose() {
        RegulatoryChange c = RegulatoryChange.create(tenant, UUID.randomUUID(), "POPIA", UUID.randomUUID(), "2013", UUID.randomUUID(), "2024",
                ChangeKind.PUBLISHED, "summary", 1, 2, 3, 4, user, "Publisher");
        assertTrue(c.isOpen());
        assertEquals(4, c.getCarriedCount());
        c.close();
        var closedAt = c.getClosedAt();
        c.close();
        assertFalse(c.isOpen());
        assertEquals(closedAt, c.getClosedAt());
    }

    @Test
    @DisplayName("carry-forward keeps the decision, is always APPROVED and points back at its source")
    void carryForward() {
        ApplicabilityAssessment src = ApplicabilityAssessment.create(tenant, UUID.randomUUID(), UUID.randomUUID(), ApplicabilityDecision.NOT_APPLICABLE,
                "We hold no personal information of minors", ApplicabilitySuggestion.NEEDS_REVIEW, List.of("r"), user, "Assessor");
        src.approve(UUID.randomUUID(), "Approver", "Agreed");
        UUID newReq = UUID.randomUUID(), newVer = UUID.randomUUID();

        ApplicabilityAssessment moved = ApplicabilityAssessment.carryForward(tenant, src, newReq, newVer, "2013", user, "Publisher");

        assertEquals(AssessmentStatus.APPROVED, moved.getStatus());
        assertEquals(ApplicabilityDecision.NOT_APPLICABLE, moved.getDecision());
        assertEquals(src.getJustification(), moved.getJustification());
        assertEquals("Approver", moved.getDecidedByName());
        assertEquals(newReq, moved.getRequirementId());
        assertEquals(newVer, moved.getVersionId());
        assertEquals(src.getId(), moved.getCarriedForwardFrom());
        assertTrue(moved.getDecisionNote().contains("2013"));
    }

    @Test
    @DisplayName("relink moves an obligation to the new requirement; a null assessment keeps the existing link")
    void relink() {
        RegulatoryObligation o = obligation();
        UUID newReq = UUID.randomUUID(), newVer = UUID.randomUUID(), oldAssessment = o.getAssessmentId();
        o.relinkRequirement(newReq, newVer, null);
        assertEquals(newReq, o.getRequirementId());
        assertEquals(newVer, o.getSourceVersionId());
        assertEquals(oldAssessment, o.getAssessmentId());
        UUID a = UUID.randomUUID();
        o.relinkRequirement(newReq, newVer, a);
        assertEquals(a, o.getAssessmentId());
    }

    @Test
    @DisplayName("detach makes the obligation hand-managed and refresh updates wording but not owner or dates")
    void detachAndRefresh() {
        RegulatoryObligation o = obligation();
        var owner = o.getResponsibleUserName();
        var review = o.getReviewDate();
        o.refreshFromRequirement("New title", "POPIA s9", "New text", "New evidence");
        assertEquals("New title", o.getTitle());
        assertEquals("New evidence", o.getEvidenceRequirement());
        assertEquals(owner, o.getResponsibleUserName());
        assertEquals(review, o.getReviewDate());
        o.detachRequirement();
        assertNull(o.getRequirementId());
        assertNull(o.getAssessmentId());
    }

    private RegulatoryObligation obligation() {
        return RegulatoryObligation.createFromRequirement(tenant, "Title", ObligationCategory.POPIA, "POPIA s9", "Text",
                UUID.randomUUID(), "Owner", LocalDate.now().plusDays(90), RecurrenceInterval.ANNUALLY,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Dept", "Evidence", user);
    }
}
