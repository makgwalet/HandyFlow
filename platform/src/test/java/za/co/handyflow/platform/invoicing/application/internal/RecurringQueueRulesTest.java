package za.co.handyflow.platform.invoicing.application.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.invoicing.application.internal.RecurringQueueRules.*;

class RecurringQueueRulesTest {

    @Test
    void nothingIsQueuedWhenTheCapIsNotEnforced() {
        assertThat(shouldQueue(false, true, false)).isFalse();
        assertThat(shouldQueue(false, false, true)).isFalse();
    }

    @Test
    void anOccurrenceIsQueuedWhenTheCapIsFull() {
        assertThat(shouldQueue(true, false, false)).isTrue();
    }

    @Test
    void anOccurrenceWaitsBehindEarlierOnesEvenWhenThereIsRoom() {
        assertThat(shouldQueue(true, true, true)).isTrue();
        assertThat(shouldQueue(true, true, false)).isFalse();
    }

    @Test
    void theServicePeriodEndsTheDayBeforeTheNextOccurrence() {
        assertThat(periodEnd(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(periodEnd(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void overdueMeansWaitingLongerThanTheLimitFromTheOriginalBillingDate() {
        LocalDate scheduled = LocalDate.of(2026, 11, 1);
        assertThat(daysDelayed(scheduled, LocalDate.of(2026, 11, 8))).isEqualTo(7);
        assertThat(overdue(scheduled, LocalDate.of(2026, 11, 8), 7)).isFalse();
        assertThat(overdue(scheduled, LocalDate.of(2026, 11, 9), 7)).isTrue();
        assertThat(daysDelayed(scheduled, LocalDate.of(2026, 10, 1))).isZero();
    }

    @Test
    void aDelayedInvoiceNamesTheServicePeriodItCovers() {
        assertThat(delayedTitle("Monthly retainer", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                .isEqualTo("Monthly retainer (service period 2026-11-01 to 2026-11-30)");
    }
}
