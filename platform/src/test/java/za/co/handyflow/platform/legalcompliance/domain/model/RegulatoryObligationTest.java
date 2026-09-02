package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain JUnit, no Spring context — exercises RegulatoryObligation's own
 * state machine directly, same style as this codebase's other pure
 * domain-entity unit tests (no repository, no DB).
 */
class RegulatoryObligationTest {

    private final TenantId tenantId = TenantId.generate();

    private RegulatoryObligation newObligation(LocalDate reviewDate, RecurrenceInterval recurrence) {
        return RegulatoryObligation.create(tenantId, "Annual CIPC return", ObligationCategory.COMPANIES_ACT,
                "Companies Act 71 of 2008", "File annual return", null, null,
                reviewDate, recurrence, UUID.randomUUID());
    }

    @Test
    @DisplayName("create() starts COMPLIANT")
    void createStartsCompliant() {
        RegulatoryObligation o = newObligation(LocalDate.now().plusMonths(1), RecurrenceInterval.ANNUALLY);
        assertEquals(ObligationStatus.COMPLIANT, o.getStatus());
        assertEquals(tenantId, o.getTenantId());
    }

    @Test
    @DisplayName("refreshStatus() sets OVERDUE when reviewDate is in the past")
    void refreshStatusOverdue() {
        RegulatoryObligation o = newObligation(LocalDate.now().minusDays(1), RecurrenceInterval.ANNUALLY);
        o.refreshStatus(LocalDate.now(), 14);
        assertEquals(ObligationStatus.OVERDUE, o.getStatus());
    }

    @Test
    @DisplayName("refreshStatus() sets DUE_SOON within the threshold window")
    void refreshStatusDueSoon() {
        RegulatoryObligation o = newObligation(LocalDate.now().plusDays(5), RecurrenceInterval.ANNUALLY);
        o.refreshStatus(LocalDate.now(), 14);
        assertEquals(ObligationStatus.DUE_SOON, o.getStatus());
    }

    @Test
    @DisplayName("refreshStatus() sets COMPLIANT outside the threshold window")
    void refreshStatusCompliant() {
        RegulatoryObligation o = newObligation(LocalDate.now().plusDays(60), RecurrenceInterval.ANNUALLY);
        o.refreshStatus(LocalDate.now(), 14);
        assertEquals(ObligationStatus.COMPLIANT, o.getStatus());
    }

    @Test
    @DisplayName("refreshStatus() never overwrites NON_COMPLIANT, even when overdue")
    void refreshStatusNeverClearsNonCompliant() {
        RegulatoryObligation o = newObligation(LocalDate.now().minusDays(1), RecurrenceInterval.ANNUALLY);
        o.markNonCompliant("Failed inspection");
        o.refreshStatus(LocalDate.now(), 14);
        assertEquals(ObligationStatus.NON_COMPLIANT, o.getStatus());
    }

    @Test
    @DisplayName("markReviewed() rolls ANNUALLY forward by one year and clears back to COMPLIANT")
    void markReviewedRollsAnnualForward() {
        LocalDate original = LocalDate.now().minusDays(1);
        RegulatoryObligation o = newObligation(original, RecurrenceInterval.ANNUALLY);
        o.markNonCompliant("was failing");

        o.markReviewed(UUID.randomUUID(), "Jane Reviewer", "Fixed now");

        assertEquals(ObligationStatus.COMPLIANT, o.getStatus());
        assertEquals(LocalDate.now().plusYears(1), o.getReviewDate());
        assertEquals("Fixed now", o.getNotes());
        assertNotNull(o.getLastReviewedAt());
        assertEquals("Jane Reviewer", o.getLastReviewedByName());
    }

    @Test
    @DisplayName("markReviewed() on a ONCE obligation does not move the review date")
    void markReviewedOnceDoesNotRoll() {
        LocalDate original = LocalDate.now().plusDays(3);
        RegulatoryObligation o = newObligation(original, RecurrenceInterval.ONCE);

        o.markReviewed(UUID.randomUUID(), "Jane Reviewer", null);

        assertEquals(original, o.getReviewDate());
    }

    @Test
    @DisplayName("markReviewed() with blank notes leaves existing notes untouched")
    void markReviewedBlankNotesLeavesExisting() {
        RegulatoryObligation o = newObligation(LocalDate.now().plusDays(3), RecurrenceInterval.MONTHLY);
        o.markNonCompliant("original notes");

        o.markReviewed(UUID.randomUUID(), "Jane Reviewer", "   ");

        assertEquals("original notes", o.getNotes());
    }

    @Test
    @DisplayName("softDelete() marks the obligation deleted")
    void softDeleteMarksDeleted() {
        RegulatoryObligation o = newObligation(LocalDate.now().plusDays(3), RecurrenceInterval.MONTHLY);
        assertFalse(o.isDeleted());
        o.softDelete(UUID.randomUUID());
        assertTrue(o.isDeleted());
    }

    @Test
    @DisplayName("linkContract() sets the linked contract id")
    void linkContractSetsId() {
        RegulatoryObligation o = newObligation(LocalDate.now().plusDays(3), RecurrenceInterval.MONTHLY);
        UUID contractId = UUID.randomUUID();
        o.linkContract(contractId);
        assertEquals(contractId, o.getLinkedContractId());
    }
}
