package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractTemplateLegalReviewTest {

    private ContractTemplateLegalReview review(String outcome, String issues, LocalDate next) {
        return ContractTemplateLegalReview.record(UUID.randomUUID(), UUID.randomUUID(), 1, "A. Attorney", "Admitted attorney",
                "Firm", LocalDate.of(2026, 11, 1), "Employment", null, "scope", issues, outcome, next, null, null);
    }

    @Test
    void defaultsJurisdictionToSouthAfrica() {
        assertEquals("South Africa", review("APPROVED", null, null).getJurisdiction());
    }

    @Test
    void conditionalApprovalNeedsOutstandingIssues() {
        assertThrows(IllegalArgumentException.class, () -> review("CONDITIONAL", " ", null));
        assertDoesNotThrow(() -> review("CONDITIONAL", "Fix clause 4", null));
    }

    @Test
    void rejectsUnknownOutcome() {
        assertThrows(IllegalArgumentException.class, () -> review("MAYBE", null, null));
    }

    @Test
    void expiresAfterTheNextReviewDate() {
        ContractTemplateLegalReview r = review("APPROVED", null, LocalDate.of(2026, 12, 1));
        assertFalse(r.isExpired(LocalDate.of(2026, 12, 1)));
        assertTrue(r.isExpired(LocalDate.of(2026, 12, 2)));
        assertFalse(review("APPROVED", null, null).isExpired(LocalDate.of(2030, 1, 1)));
    }

    @Test
    void withdrawalIsRecorded() {
        ContractTemplateLegalReview r = review("APPROVED", null, null);
        r.withdraw("Law changed", UUID.randomUUID());
        assertTrue(r.isWithdrawn());
        assertEquals("Law changed", r.getWithdrawnReason());
    }
}
