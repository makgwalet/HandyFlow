package za.co.handyflow.platform.contracting.scheduler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ContractObligationReminderRecipientsTest {

    @Test
    void anEnteredAddressWinsAndNothingElseIsLookedUp() {
        AtomicInteger lookups = new AtomicInteger();
        List<String> to = ContractObligationReminderScheduler.recipientEmails(" owner@x.co.za ",
                () -> { lookups.incrementAndGet(); return List.of("creator@x.co.za"); },
                () -> { lookups.incrementAndGet(); return List.of("admin@x.co.za"); });
        assertEquals(List.of("owner@x.co.za"), to);
        assertEquals(0, lookups.get());
    }

    @Test
    void withNoAddressTheContractCreatorIsUsed() {
        assertEquals(List.of("creator@x.co.za"), ContractObligationReminderScheduler.recipientEmails(null,
                () -> List.of("creator@x.co.za"), () -> List.of("admin@x.co.za")));
        assertEquals(List.of("creator@x.co.za"), ContractObligationReminderScheduler.recipientEmails("  ",
                () -> List.of("creator@x.co.za"), () -> List.of("admin@x.co.za")));
    }

    @Test
    void withNoCreatorTheTenantAdministratorsAreUsed() {
        assertEquals(List.of("a1@x.co.za", "a2@x.co.za"), ContractObligationReminderScheduler.recipientEmails(null,
                List::of, () -> List.of("a1@x.co.za", "a2@x.co.za")));
    }

    @Test
    void withNoOneAtAllTheResultIsEmptySoTheJobCanLogAndMoveOn() {
        assertTrue(ContractObligationReminderScheduler.recipientEmails(null, List::of, List::of).isEmpty());
    }
}
