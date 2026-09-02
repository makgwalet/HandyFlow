package za.co.handyflow.platform.collectionsagency.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CollAgencyDebtorAccountTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    private CollAgencyDebtorAccount newAccount() {
        return CollAgencyDebtorAccount.create(tenantId, clientId, null, "REF-001", "John Debtor", "8001015800083",
                "john@example.com", "0821234567", "1 Debtor St", "Acme Retailers", LocalDate.now().minusMonths(2),
                new BigDecimal("1000.00"), LocalDate.now(), null);
    }

    @Test
    void createRejectsBlankDebtorName() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyDebtorAccount.create(tenantId, clientId, null,
                "REF-001", "", "id", "e", "p", "a", "Acme", LocalDate.now(), new BigDecimal("100"), LocalDate.now(), null));
    }

    @Test
    void createRejectsBlankOriginalCreditorName() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyDebtorAccount.create(tenantId, clientId, null,
                "REF-001", "John", "id", "e", "p", "a", " ", LocalDate.now(), new BigDecimal("100"), LocalDate.now(), null));
    }

    @Test
    void createRejectsNonPositiveDebtAmount() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyDebtorAccount.create(tenantId, clientId, null,
                "REF-001", "John", "id", "e", "p", "a", "Acme", LocalDate.now(), BigDecimal.ZERO, LocalDate.now(), null));
    }

    @Test
    void newAccountStartsPlacedWithFullBalance() {
        CollAgencyDebtorAccount a = newAccount();
        assertEquals("PLACED", a.getStatus());
        assertEquals(new BigDecimal("1000.00"), a.getCurrentBalance());
    }

    @Test
    void applyPaymentReducesBalanceAndMovesToInProgress() {
        CollAgencyDebtorAccount a = newAccount();
        a.applyPayment(new BigDecimal("300.00"));
        assertEquals(new BigDecimal("700.00"), a.getCurrentBalance());
        assertEquals("IN_PROGRESS", a.getStatus());
    }

    @Test
    void applyPaymentClearingFullBalanceAutoTransitionsToRecovered() {
        CollAgencyDebtorAccount a = newAccount();
        a.applyPayment(new BigDecimal("1000.00"));
        assertEquals(BigDecimal.ZERO, a.getCurrentBalance());
        assertEquals("RECOVERED", a.getStatus());
        assertNotNull(a.getClosedDate());
    }

    @Test
    void applyPaymentOverpayingClampsBalanceToZeroAndRecovers() {
        CollAgencyDebtorAccount a = newAccount();
        a.applyPayment(new BigDecimal("1500.00"));
        assertEquals(BigDecimal.ZERO, a.getCurrentBalance());
        assertEquals("RECOVERED", a.getStatus());
    }

    @Test
    void assignAndAdvanceStatusRejectedOnTerminalAccount() {
        CollAgencyDebtorAccount a = newAccount();
        a.advanceStatus("WRITTEN_OFF");
        assertThrows(IllegalStateException.class, () -> a.assign(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> a.advanceStatus("IN_PROGRESS"));
    }

    @Test
    void advanceStatusToClosedFamilySetsClosedDate() {
        CollAgencyDebtorAccount a = newAccount();
        a.advanceStatus("RETURNED_TO_CLIENT");
        assertEquals("RETURNED_TO_CLIENT", a.getStatus());
        assertNotNull(a.getClosedDate());
    }

    @Test
    void softDeleteMarksDeleted() {
        CollAgencyDebtorAccount a = newAccount();
        assertFalse(a.isDeleted());
        a.softDelete();
        assertTrue(a.isDeleted());
    }
}
