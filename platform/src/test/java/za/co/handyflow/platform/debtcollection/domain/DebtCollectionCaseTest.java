package za.co.handyflow.platform.debtcollection.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.ClosureReason;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DebtCollectionCaseTest {

    private final TenantId tenantId = TenantId.generate();

    private DebtCollectionCase openCase() {
        return DebtCollectionCase.open(tenantId, "DC-00001", null, "Jane Debtor", "jane@example.com", "0821234567",
                new BigDecimal("1500.00"), Set.of(UUID.randomUUID()), LocalDate.now(), null, null, "notes",
                UUID.randomUUID());
    }

    @Test
    @DisplayName("open() starts OPEN with the given case number and outstanding total")
    void openSetsInitialStatusToOpen() {
        DebtCollectionCase c = openCase();
        assertEquals(CaseStatus.OPEN, c.getStatus());
        assertEquals(0, new BigDecimal("1500.00").compareTo(c.getTotalOutstanding()));
        assertEquals("DC-00001", c.getCaseNumber());
    }

    @Test
    @DisplayName("open() rejects a blank debtor name")
    void openRejectsBlankDebtorName() {
        assertThrows(IllegalArgumentException.class, () -> DebtCollectionCase.open(tenantId, "DC-00001", null,
                "  ", null, null, new BigDecimal("100"), Set.of(UUID.randomUUID()), LocalDate.now(), null, null,
                null, UUID.randomUUID()));
    }

    @Test
    @DisplayName("open() rejects a non-positive outstanding amount")
    void openRejectsNonPositiveOutstanding() {
        assertThrows(IllegalArgumentException.class, () -> DebtCollectionCase.open(tenantId, "DC-00001", null,
                "Jane", null, null, BigDecimal.ZERO, Set.of(UUID.randomUUID()), LocalDate.now(), null, null, null,
                UUID.randomUUID()));
    }

    @Test
    @DisplayName("advanceStatus() moves to a non-terminal status")
    void advanceStatusMovesToNonTerminalStatus() {
        DebtCollectionCase c = openCase();
        c.advanceStatus(CaseStatus.DEMAND_SENT);
        assertEquals(CaseStatus.DEMAND_SENT, c.getStatus());
    }

    @Test
    @DisplayName("advanceStatus() rejects CLOSED as a target — close() must be used instead")
    void advanceStatusRejectsClosedAsTarget() {
        DebtCollectionCase c = openCase();
        assertThrows(IllegalArgumentException.class, () -> c.advanceStatus(CaseStatus.CLOSED));
    }

    @Test
    @DisplayName("advanceStatus() rejects any change once the case is CLOSED")
    void advanceStatusRejectsWhenAlreadyClosed() {
        DebtCollectionCase c = openCase();
        c.close(ClosureReason.PAID_IN_FULL, "paid");
        assertThrows(IllegalStateException.class, () -> c.advanceStatus(CaseStatus.DEMAND_SENT));
    }

    @Test
    @DisplayName("close() requires a ClosureReason")
    void closeRequiresReason() {
        DebtCollectionCase c = openCase();
        assertThrows(IllegalArgumentException.class, () -> c.close(null, "notes"));
    }

    @Test
    @DisplayName("close() is terminal and records closedDate")
    void closeIsTerminalAndSetsClosedDate() {
        DebtCollectionCase c = openCase();
        c.close(ClosureReason.SETTLED_PARTIAL, "settled for less");
        assertEquals(CaseStatus.CLOSED, c.getStatus());
        assertEquals(ClosureReason.SETTLED_PARTIAL, c.getClosureReason());
        assertEquals(LocalDate.now(), c.getClosedDate());
        assertThrows(IllegalStateException.class, () -> c.close(ClosureReason.OTHER, null));
    }

    @Test
    @DisplayName("writeOff() requires a positive amount and moves status to WRITTEN_OFF")
    void writeOffRequiresPositiveAmountAndSetsStatus() {
        DebtCollectionCase c = openCase();
        assertThrows(IllegalArgumentException.class, () -> c.writeOff(BigDecimal.ZERO, "uncollectable"));

        c.writeOff(new BigDecimal("1500.00"), "debtor untraceable");
        assertEquals(CaseStatus.WRITTEN_OFF, c.getStatus());
        assertEquals(0, new BigDecimal("1500.00").compareTo(c.getWriteOffAmount()));
    }

    @Test
    @DisplayName("writeOff() is rejected once the case is CLOSED")
    void writeOffRejectedOnClosedCase() {
        DebtCollectionCase c = openCase();
        c.close(ClosureReason.OTHER, null);
        assertThrows(IllegalStateException.class, () -> c.writeOff(new BigDecimal("100"), "reason"));
    }

    @Test
    @DisplayName("recordContact() only ever advances lastContactDate forward")
    void recordContactOnlyAdvancesLastContactDateForward() {
        DebtCollectionCase c = openCase();
        LocalDate later = LocalDate.now().plusDays(5);
        LocalDate earlier = LocalDate.now().minusDays(5);

        c.recordContact(later);
        assertEquals(later, c.getLastContactDate());

        c.recordContact(earlier);
        assertEquals(later, c.getLastContactDate()); // does not regress
    }

    @Test
    @DisplayName("linkInvoice()/unlinkInvoice() mutate the linked-invoice set")
    void linkAndUnlinkInvoiceMutateTheSet() {
        DebtCollectionCase c = openCase();
        UUID invoiceId = UUID.randomUUID();
        c.linkInvoice(invoiceId);
        assertTrue(c.getLinkedInvoiceIds().contains(invoiceId));
        c.unlinkInvoice(invoiceId);
        assertFalse(c.getLinkedInvoiceIds().contains(invoiceId));
    }

    @Test
    @DisplayName("softDelete() marks the case deleted")
    void softDeleteMarksDeleted() {
        DebtCollectionCase c = openCase();
        UUID deletedBy = UUID.randomUUID();
        c.softDelete(deletedBy);
        assertTrue(c.isDeleted());
        assertEquals(deletedBy, c.getDeletedBy());
    }
}
