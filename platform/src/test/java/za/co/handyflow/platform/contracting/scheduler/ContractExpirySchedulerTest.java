package za.co.handyflow.platform.contracting.scheduler;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ContractExpirySchedulerTest {

    Contract contract(boolean autoRenew) {
        LocalDate today = LocalDate.now();
        Contract c = Contract.create(TenantId.generate(), "C-1", "Lease", "LEASE", "<p>b</p>", null, null);
        c.setDates(today.minusDays(400), today.minusDays(1));
        c.setAutoRenew(autoRenew, 30);
        c.markSigned();
        return c;
    }

    @Test
    void aFixedTermContractPastItsEndDateExpires() {
        assertTrue(ContractExpiryScheduler.shouldExpire(contract(false), false));
    }

    @Test
    void anAutoRenewingContractIsLeftSignedUntilSomeoneDecides() {
        assertFalse(ContractExpiryScheduler.shouldExpire(contract(true), false));
    }

    @Test
    void onceARenewalDecisionIsRecordedTheAutoRenewingContractCanExpire() {
        assertTrue(ContractExpiryScheduler.shouldExpire(contract(true), true));
    }
}
