package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractClauseTest {

    LocalDate today = LocalDate.of(2026, 11, 18);

    ContractClause draft() {
        return ContractClause.create(UUID.randomUUID(), "Force majeure", "FORCE_MAJEURE", "<p>Wording from the reviewer.</p>", "Contract law", null, UUID.randomUUID());
    }

    ContractClause approved(LocalDate next) {
        ContractClause c = draft();
        c.approve("A Attorney", "Admitted attorney", today.minusDays(1), next, null, today);
        return c;
    }

    @Test
    void newClauseIsADraftInItsOwnFamilyWithSanitisedWording() {
        ContractClause c = ContractClause.create(UUID.randomUUID(), "T", "OTHER", "<p>ok</p><script>x()</script>", null, null, null);
        assertEquals("DRAFT", c.getStatus());
        assertEquals(1, c.getVersionNo());
        assertEquals(c.getId(), c.getFamilyId());
        assertEquals("South Africa", c.getJurisdiction());
        assertFalse(c.getBody().contains("script"));
    }

    @Test
    void requiresTitleKnownCategoryAndWording() {
        UUID t = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> ContractClause.create(t, " ", "OTHER", "<p>x</p>", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractClause.create(t, "T", "NOPE", "<p>x</p>", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractClause.create(t, "T", "OTHER", "  ", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractClause.create(t, "T", "OTHER", "<script>x()</script>", null, null, null));
    }

    @Test
    void approvalNeedsTheReviewerAndASensibleDate() {
        ContractClause c = draft();
        assertThrows(IllegalArgumentException.class, () -> c.approve(" ", "x", today, null, null, today));
        assertThrows(IllegalArgumentException.class, () -> c.approve("A", " ", today, null, null, today));
        assertThrows(IllegalArgumentException.class, () -> c.approve("A", "x", today.plusDays(1), null, null, today));   // future review date
        assertThrows(IllegalArgumentException.class, () -> c.approve("A", "x", today, today, null, today));                // next review not after
        assertEquals("DRAFT", c.getStatus());
    }

    @Test
    void onlyADraftCanBeEditedAndEditingAfterReviewIsNotPossible() {
        ContractClause c = approved(null);
        assertEquals("APPROVED", c.getStatus());
        assertThrows(IllegalStateException.class, () -> c.edit("T", "OTHER", "<p>changed</p>", null, null));
        c.returnToDraft();
        assertNull(c.getReviewerName());          // the approval belonged to the old wording
        c.edit("T", "OTHER", "<p>changed</p>", null, null);
        assertEquals("<p>changed</p>", c.getBody());
    }

    @Test
    void publishNeedsApprovalAndACurrentReview() {
        assertThrows(IllegalStateException.class, () -> draft().publish(today));
        ContractClause lapsed = approved(today.plusDays(10));
        assertThrows(IllegalStateException.class, () -> lapsed.publish(today.plusDays(11)));
        ContractClause c = approved(today.plusDays(10));
        c.publish(today);
        assertEquals("PUBLISHED", c.getStatus());
        assertTrue(c.usable(today));
    }

    @Test
    void publishedClauseStopsBeingUsableWhenItsReviewLapses() {
        ContractClause c = approved(today.plusDays(10));
        c.publish(today);
        assertTrue(c.usable(today.plusDays(10)));
        assertFalse(c.usable(today.plusDays(11)));
        assertTrue(c.reviewExpired(today.plusDays(11)));
    }

    @Test
    void newVersionIsADraftInTheSameFamilyAndNeedsItsOwnReview() {
        ContractClause c = approved(null);
        c.publish(today);
        ContractClause v2 = c.newVersion(2, null);
        assertEquals(c.getFamilyId(), v2.getFamilyId());
        assertEquals(2, v2.getVersionNo());
        assertEquals("DRAFT", v2.getStatus());
        assertNull(v2.getReviewerName());
        assertNotEquals(c.getId(), v2.getId());
        assertThrows(IllegalStateException.class, () -> draft().newVersion(2, null));
    }

    @Test
    void withdrawNeedsAReasonAndALiveClause() {
        ContractClause c = approved(null);
        c.publish(today);
        assertThrows(IllegalArgumentException.class, () -> c.withdraw(" "));
        c.withdraw("Reviewer changed their view");
        assertEquals("WITHDRAWN", c.getStatus());
        assertFalse(c.usable(today));
        assertThrows(IllegalStateException.class, () -> c.withdraw("again"));
        assertThrows(IllegalStateException.class, () -> draft().withdraw("x"));
    }
}
