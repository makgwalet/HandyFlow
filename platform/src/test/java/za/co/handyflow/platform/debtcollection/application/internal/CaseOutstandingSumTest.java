package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaseOutstandingSumTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID paid = UUID.randomUUID();

    private final Map<UUID, BigDecimal> outstanding = Map.of(a, new BigDecimal("100.00"), b, new BigDecimal("50.50"));

    @Test
    void sumsOutstandingInvoices() {
        assertEquals(new BigDecimal("150.50"), DebtCollectionCaseService.sumOutstanding(outstanding, Set.of(a, b), true));
        assertEquals(new BigDecimal("150.50"), DebtCollectionCaseService.sumOutstanding(outstanding, Set.of(a, b), false));
    }

    @Test
    void strictModeRejectsAnInvoiceThatIsNotOutstanding() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> DebtCollectionCaseService.sumOutstanding(outstanding, Set.of(a, paid), true));
        assertEquals(true, ex.getMessage().contains(paid.toString()));
    }

    @Test
    void tolerantModeCountsAPaidLinkedInvoiceAsZero() {
        // the bug this fixes: one paid invoice used to make refresh/unlink/link throw for the whole case
        assertEquals(new BigDecimal("100.00"), DebtCollectionCaseService.sumOutstanding(outstanding, Set.of(a, paid), false));
    }

    @Test
    void allInvoicesPaidRefreshesToZeroWithoutThrowing() {
        assertEquals(BigDecimal.ZERO, DebtCollectionCaseService.sumOutstanding(outstanding, Set.of(paid), false));
    }
}
