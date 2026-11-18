package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractObligationTest {

    UUID tenant = UUID.randomUUID(), contract = UUID.randomUUID();
    LocalDate today = LocalDate.of(2026, 11, 18);

    ContractObligation make(String recurrence, String email, LocalDate due) {
        return ContractObligation.create(tenant, contract, "Pay deposit", null, "PAYMENT", "OURS", email, due, recurrence, UUID.randomUUID());
    }

    @Test
    void requiresTitleDueDateSideAndValidEmail() {
        assertThrows(IllegalArgumentException.class, () -> ContractObligation.create(tenant, contract, " ", null, "OTHER", "OURS", null, today, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractObligation.create(tenant, contract, "x", null, "OTHER", "OURS", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractObligation.create(tenant, contract, "x", null, "OTHER", "NOBODY", null, today, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractObligation.create(tenant, contract, "x", null, "OTHER", "OURS", "not-an-email", today, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractObligation.create(tenant, contract, "x", null, "BOGUS", "OURS", null, today, null, null));
    }

    @Test
    void overdueOnlyWhenOpenAndPastDue() {
        ContractObligation o = make("NONE", null, today.minusDays(1));
        assertTrue(o.isOverdue(today));
        o.complete("done", null);
        assertFalse(o.isOverdue(today));
    }

    @Test
    void nonRecurringCompletionHasNoNextOccurrence() {
        assertEquals(Optional.empty(), make("NONE", null, today).complete("paid", UUID.randomUUID()));
    }

    @Test
    void recurringCompletionDatesNextFromDueDateNotCompletionDate() {
        ContractObligation o = make("MONTHLY", "a@b.co", LocalDate.of(2026, 10, 31));
        ContractObligation next = o.complete(null, null).orElseThrow();
        assertEquals(LocalDate.of(2026, 11, 30), next.getDueDate());
        assertEquals("OPEN", next.getStatus());
        assertEquals("a@b.co", next.getOwnerEmail());
        assertNotEquals(o.getId(), next.getId());
        assertEquals(LocalDate.of(2027, 1, 15), make("QUARTERLY", null, LocalDate.of(2026, 10, 15)).complete(null, null).orElseThrow().getDueDate());
        assertEquals(LocalDate.of(2027, 3, 1), make("ANNUALLY", null, LocalDate.of(2026, 3, 1)).complete(null, null).orElseThrow().getDueDate());
    }

    @Test
    void closedObligationCannotBeEditedCompletedOrWaivedAgain() {
        ContractObligation o = make("NONE", null, today);
        o.waive("no longer needed");
        assertEquals("WAIVED", o.getStatus());
        assertThrows(IllegalStateException.class, () -> o.complete(null, null));
        assertThrows(IllegalStateException.class, () -> o.waive("again"));
        assertThrows(IllegalStateException.class, () -> o.edit("t", null, "OTHER", "OURS", null, today, "NONE"));
    }

    @Test
    void waiveNeedsAReason() {
        assertThrows(IllegalArgumentException.class, () -> make("NONE", null, today).waive(" "));
    }

    @Test
    void remindersAtSevenOneZeroDaysThenWeeklyWhenOverdue() {
        assertTrue(make("NONE", "a@b.co", today.plusDays(7)).reminderDue(today));
        assertTrue(make("NONE", "a@b.co", today.plusDays(1)).reminderDue(today));
        assertTrue(make("NONE", "a@b.co", today).reminderDue(today));
        assertFalse(make("NONE", "a@b.co", today.plusDays(3)).reminderDue(today));
        ContractObligation late = make("NONE", "a@b.co", today.minusDays(2));
        assertTrue(late.reminderDue(today));
        late.markReminded(today.minusDays(3));
        assertFalse(late.reminderDue(today));
        late.markReminded(today.minusDays(7));
        assertTrue(late.reminderDue(today));
    }

    @Test
    void neverRemindedTwiceInADayOrWithoutAnAddress() {
        ContractObligation o = make("NONE", "a@b.co", today);
        o.markReminded(today);
        assertFalse(o.reminderDue(today));
        assertFalse(make("NONE", null, today).reminderDue(today));
    }
}
