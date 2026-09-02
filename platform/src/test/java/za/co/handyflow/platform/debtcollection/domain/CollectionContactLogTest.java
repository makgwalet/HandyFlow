package za.co.handyflow.platform.debtcollection.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.domain.model.CollectionContactLog;
import za.co.handyflow.platform.debtcollection.domain.model.ContactMethod;
import za.co.handyflow.platform.debtcollection.domain.model.ContactOutcome;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CollectionContactLogTest {

    private final TenantId tenantId = TenantId.generate();

    @Test
    @DisplayName("record() requires a caseId")
    void requiresCaseId() {
        assertThrows(IllegalArgumentException.class, () -> CollectionContactLog.record(tenantId, null,
                LocalDate.now(), ContactMethod.PHONE_CALL, ContactOutcome.NO_ANSWER, null, null, null,
                UUID.randomUUID(), "Staff"));
    }

    @Test
    @DisplayName("record() requires contactMethod and outcome")
    void requiresMethodAndOutcome() {
        UUID caseId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> CollectionContactLog.record(tenantId, caseId,
                LocalDate.now(), null, ContactOutcome.NO_ANSWER, null, null, null, UUID.randomUUID(), "Staff"));
        assertThrows(IllegalArgumentException.class, () -> CollectionContactLog.record(tenantId, caseId,
                LocalDate.now(), ContactMethod.PHONE_CALL, null, null, null, null, UUID.randomUUID(), "Staff"));
    }

    @Test
    @DisplayName("record() requires promisedPaymentDate and promisedPaymentAmount when outcome is PROMISE_TO_PAY")
    void promiseToPayRequiresPromisedPaymentDetails() {
        UUID caseId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> CollectionContactLog.record(tenantId, caseId,
                LocalDate.now(), ContactMethod.PHONE_CALL, ContactOutcome.PROMISE_TO_PAY, null, null, null,
                UUID.randomUUID(), "Staff"));

        CollectionContactLog log = CollectionContactLog.record(tenantId, caseId, LocalDate.now(),
                ContactMethod.PHONE_CALL, ContactOutcome.PROMISE_TO_PAY, "will pay Friday",
                LocalDate.now().plusDays(3), new BigDecimal("500.00"), UUID.randomUUID(), "Staff");
        assertEquals(ContactOutcome.PROMISE_TO_PAY, log.getOutcome());
        assertEquals(0, new BigDecimal("500.00").compareTo(log.getPromisedPaymentAmount()));
    }

    @Test
    @DisplayName("record() defaults contactDate to today when not supplied")
    void defaultsContactDateToToday() {
        CollectionContactLog log = CollectionContactLog.record(tenantId, UUID.randomUUID(), null,
                ContactMethod.EMAIL, ContactOutcome.NO_ANSWER, null, null, null, UUID.randomUUID(), "Staff");
        assertEquals(LocalDate.now(), log.getContactDate());
    }
}
