package za.co.handyflow.platform.collectionsagency.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The compliance-critical rule: all three NCA disclosures must be true, or record() rejects the entry outright. */
class CollAgencyContactLogTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID debtorAccountId = UUID.randomUUID();
    private final UUID recordedBy = UUID.randomUUID();

    @Test
    void recordSucceedsWhenAllThreeDisclosuresTrue() {
        CollAgencyContactLog log = CollAgencyContactLog.record(tenantId, debtorAccountId, LocalDate.now(),
                "PHONE_CALL", "NO_ANSWER", true, true, true, "notes", null, null, recordedBy, "Jane Staff");
        assertTrue(log.isDisclosedThirdPartyCollector());
        assertTrue(log.isDisclosedOriginalCreditor());
        assertTrue(log.isDisclosedDebtorRights());
    }

    @Test
    void recordRejectsWhenThirdPartyCollectorNotDisclosed() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyContactLog.record(tenantId, debtorAccountId,
                LocalDate.now(), "PHONE_CALL", "NO_ANSWER", false, true, true, null, null, null, recordedBy, "Jane"));
    }

    @Test
    void recordRejectsWhenOriginalCreditorNotDisclosed() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyContactLog.record(tenantId, debtorAccountId,
                LocalDate.now(), "PHONE_CALL", "NO_ANSWER", true, false, true, null, null, null, recordedBy, "Jane"));
    }

    @Test
    void recordRejectsWhenDebtorRightsNotDisclosed() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyContactLog.record(tenantId, debtorAccountId,
                LocalDate.now(), "PHONE_CALL", "NO_ANSWER", true, true, false, null, null, null, recordedBy, "Jane"));
    }

    @Test
    void recordRequiresPromisedPaymentDetailsWhenOutcomeIsPromiseToPay() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyContactLog.record(tenantId, debtorAccountId,
                LocalDate.now(), "PHONE_CALL", "PROMISE_TO_PAY", true, true, true, null, null, null, recordedBy, "Jane"));
    }

    @Test
    void recordSucceedsForPromiseToPayWithFullDetails() {
        CollAgencyContactLog log = CollAgencyContactLog.record(tenantId, debtorAccountId, LocalDate.now(),
                "PHONE_CALL", "PROMISE_TO_PAY", true, true, true, null, LocalDate.now().plusDays(7),
                new BigDecimal("500.00"), recordedBy, "Jane");
        assertEquals(new BigDecimal("500.00"), log.getPromisedPaymentAmount());
    }

    @Test
    void recordRejectsNullDebtorAccountId() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyContactLog.record(tenantId, null,
                LocalDate.now(), "PHONE_CALL", "NO_ANSWER", true, true, true, null, null, null, recordedBy, "Jane"));
    }
}
