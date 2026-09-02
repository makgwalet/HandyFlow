package za.co.handyflow.platform.collectionsagency.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** RECEIPT requires a debtorAccountId; REMITTANCE forbids one — same rule the V257 CHECK constraint enforces again at the DB layer (belt-and-braces, not a substitute for this). */
class CollAgencyTrustTransactionTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID debtorAccountId = UUID.randomUUID();
    private final UUID recordedBy = UUID.randomUUID();

    @Test
    void receiptRequiresDebtorAccountId() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyTrustTransaction.receipt(tenantId, clientId,
                null, new BigDecimal("100.00"), LocalDate.now(), "REF1", "notes", recordedBy));
    }

    @Test
    void receiptSucceedsWithDebtorAccountId() {
        CollAgencyTrustTransaction txn = CollAgencyTrustTransaction.receipt(tenantId, clientId, debtorAccountId,
                new BigDecimal("100.00"), LocalDate.now(), "REF1", "notes", recordedBy);
        assertEquals("RECEIPT", txn.getTransactionType());
        assertEquals(debtorAccountId, txn.getDebtorAccountId());
    }

    @Test
    void remittanceHasNullDebtorAccountId() {
        CollAgencyTrustTransaction txn = CollAgencyTrustTransaction.remittance(tenantId, clientId,
                new BigDecimal("500.00"), LocalDate.now(), "CI00001", "notes", recordedBy);
        assertEquals("REMITTANCE", txn.getTransactionType());
        assertNull(txn.getDebtorAccountId());
    }

    @Test
    void amountMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyTrustTransaction.receipt(tenantId, clientId,
                debtorAccountId, BigDecimal.ZERO, LocalDate.now(), "REF1", "notes", recordedBy));
        assertThrows(IllegalArgumentException.class, () -> CollAgencyTrustTransaction.remittance(tenantId, clientId,
                new BigDecimal("-1"), LocalDate.now(), "CI00001", "notes", recordedBy));
    }

    @Test
    void clientIdIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyTrustTransaction.receipt(tenantId, null,
                debtorAccountId, new BigDecimal("100.00"), LocalDate.now(), "REF1", "notes", recordedBy));
    }

    @Test
    void transactionDateDefaultsToTodayWhenNull() {
        CollAgencyTrustTransaction txn = CollAgencyTrustTransaction.receipt(tenantId, clientId, debtorAccountId,
                new BigDecimal("100.00"), null, "REF1", "notes", recordedBy);
        assertEquals(LocalDate.now(), txn.getTransactionDate());
    }
}
