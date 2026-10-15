package za.co.handyflow.platform.controls.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.controls.domain.model.ControlException;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ControlExceptionManagementRulesTest {
    UUID tenant = UUID.randomUUID(), alice = UUID.randomUUID(), bob = UUID.randomUUID();
    LocalDate today = LocalDate.of(2026, 10, 15);

    ControlException fresh() {
        return ControlException.raise(tenant, "scm", "THREE_WAY_MATCH", "INVOICE", UUID.randomUUID(), "WARNING", "Invoice does not match");
    }

    @Test
    void cannotResolveBeforeRating() {
        assertThrows(IllegalStateException.class, () -> fresh().resolve(bob, "Bob", "Checked and fixed it", null, today));
    }

    @Test
    void lowRiskNeedsOnlyRatingAndNotes() {
        var e = fresh();
        e.rate(alice, "Alice", "low", "Small amount, one-off");
        assertThrows(IllegalArgumentException.class, () -> e.resolve(bob, "Bob", "short", null, today));
        e.resolve(bob, "Bob", "Supplier credit note received", null, today);
        assertEquals("RESOLVED", e.getStatus());
    }

    @Test
    void seriousNeedsResponseDifferentResolverAndFollowUp() {
        var e = fresh();
        e.rate(alice, "Alice", "HIGH", "Large amount, repeated supplier");
        assertThrows(IllegalStateException.class, () -> e.resolve(bob, "Bob", "Fixed and credited back", today.plusDays(30), today));
        e.respond(alice, "Alice", "Supplier terms are being renegotiated");
        assertThrows(IllegalStateException.class, () -> e.resolve(alice, "Alice", "Fixed and credited back", today.plusDays(30), today));
        assertThrows(IllegalStateException.class, () -> e.resolve(bob, "Bob", "Fixed and credited back", null, today));
        assertThrows(IllegalArgumentException.class, () -> e.resolve(bob, "Bob", "Fixed and credited back", today, today));
        e.resolve(bob, "Bob", "Fixed and credited back", today.plusDays(30), today);
        assertEquals("RESOLVED", e.getStatus());
    }

    @Test
    void assignNeedsOwnerAndDate() {
        var e = fresh();
        assertThrows(IllegalArgumentException.class, () -> e.assign(null, "x", today));
        assertThrows(IllegalArgumentException.class, () -> e.assign(bob, "Bob", null));
        e.assign(bob, "Bob", today.minusDays(1));
        assertTrue(e.overdueOn(today));
        assertFalse(e.overdueOn(today.minusDays(5)));
    }

    @Test
    void badRatingRejected() {
        assertThrows(IllegalArgumentException.class, () -> fresh().rate(alice, "Alice", "SEVERE", "Some reason here"));
    }

    @Test
    void followUpHoldingConfirms() {
        var e = resolvedWithFollowUp();
        assertFalse(e.followUpDueOn(today.plusDays(29)));
        assertTrue(e.followUpDueOn(today.plusDays(30)));
        e.completeFollowUp(alice, "Alice", "holding", "Checked three later invoices, all matched");
        assertEquals("RESOLVED", e.getStatus());
        assertEquals("HOLDING", e.getFollowUpOutcome());
        assertFalse(e.followUpDueOn(today.plusDays(60)));
        assertThrows(IllegalStateException.class, () -> e.completeFollowUp(alice, "Alice", "HOLDING", "Checked again now"));
    }

    @Test
    void followUpRecurredReopens() {
        var e = resolvedWithFollowUp();
        e.completeFollowUp(alice, "Alice", "RECURRED", "Same supplier mismatched again");
        assertEquals("OPEN", e.getStatus());
        assertEquals(1, e.getTimesReopened());
        assertNull(e.getResolvedAt());
        assertThrows(IllegalStateException.class, () -> e.completeFollowUp(alice, "Alice", "HOLDING", "Nothing waiting now"));
    }

    @Test
    void systemResolutionKeepsOldBehaviour() {
        var e = fresh();
        e.resolveBySystem(null, "System", null);
        assertEquals("RESOLVED", e.getStatus());
        assertThrows(IllegalStateException.class, () -> e.resolveBySystem(null, "System", null));
    }

    private ControlException resolvedWithFollowUp() {
        var e = fresh();
        e.rate(alice, "Alice", "CRITICAL", "Possible duplicate payment");
        e.respond(alice, "Alice", "Payment recalled and controls tightened");
        e.resolve(bob, "Bob", "Payment recovered in full", today.plusDays(30), today);
        return e;
    }
}
