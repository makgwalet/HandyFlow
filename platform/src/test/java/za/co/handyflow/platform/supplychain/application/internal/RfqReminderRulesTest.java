package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RfqReminderRulesTest {

    private final Instant now = Instant.parse("2026-11-18T10:00:00Z");

    @Test
    void supplierIsRemindedOnlyInsideTheWindow_once_andOnlyWhenTheyHaveNotAnswered() {
        assertThat(RfqReminderRules.supplierReminderDue("INVITED", null, "OPEN", now.plusSeconds(47 * 3600), now, 48)).isTrue();
        assertThat(RfqReminderRules.supplierReminderDue("INVITED", null, "OPEN", now.plusSeconds(48 * 3600), now, 48)).as("exactly 48h still counts").isTrue();
        assertThat(RfqReminderRules.supplierReminderDue("INVITED", null, "OPEN", now.plusSeconds(49 * 3600), now, 48)).isFalse();
        assertThat(RfqReminderRules.supplierReminderDue("INVITED", null, "OPEN", now.minusSeconds(60), now, 48)).as("already closed").isFalse();
        assertThat(RfqReminderRules.supplierReminderDue("INVITED", now.minusSeconds(3600), "OPEN", now.plusSeconds(3600), now, 48)).as("already reminded").isFalse();
        assertThat(RfqReminderRules.supplierReminderDue("RESPONDED", null, "OPEN", now.plusSeconds(3600), now, 48)).isFalse();
        assertThat(RfqReminderRules.supplierReminderDue("DECLINED", null, "OPEN", now.plusSeconds(3600), now, 48)).isFalse();
        assertThat(RfqReminderRules.supplierReminderDue("INVITED", null, "CANCELLED", now.plusSeconds(3600), now, 48)).isFalse();
        assertThat(RfqReminderRules.supplierReminderDue("INVITED", null, "OPEN", null, now, 48)).as("no closing time").isFalse();
    }

    @Test
    void buyerIsToldOnceWhenAnRfqPassesItsClosingTimeStillOpen() {
        assertThat(RfqReminderRules.buyerCloseNoticeDue("OPEN", now.minusSeconds(1), null, now)).isTrue();
        assertThat(RfqReminderRules.buyerCloseNoticeDue("OPEN", now, null, now)).isTrue();
        assertThat(RfqReminderRules.buyerCloseNoticeDue("OPEN", now.plusSeconds(1), null, now)).isFalse();
        assertThat(RfqReminderRules.buyerCloseNoticeDue("OPEN", now.minusSeconds(1), now.minusSeconds(5), now)).isFalse();
        assertThat(RfqReminderRules.buyerCloseNoticeDue("CLOSED", now.minusSeconds(1), null, now)).as("someone already closed it").isFalse();
        assertThat(RfqReminderRules.buyerCloseNoticeDue("OPEN", null, null, now)).isFalse();
    }

    @Test
    void quotationExpiryNoticeCoversTheNextSevenDays_forUndecidedRfqsOnly() {
        LocalDate today = LocalDate.parse("2026-11-18");
        assertThat(RfqReminderRules.quotationExpiryNoticeDue("SUBMITTED", "CLOSED", today, null, today, 7)).isTrue();
        assertThat(RfqReminderRules.quotationExpiryNoticeDue("SUBMITTED", "OPEN", today.plusDays(7), null, today, 7)).isTrue();
        assertThat(RfqReminderRules.quotationExpiryNoticeDue("SUBMITTED", "CLOSED", today.plusDays(8), null, today, 7)).isFalse();
        assertThat(RfqReminderRules.quotationExpiryNoticeDue("SUBMITTED", "CLOSED", today.minusDays(1), null, today, 7)).as("already expired").isFalse();
        assertThat(RfqReminderRules.quotationExpiryNoticeDue("SUBMITTED", "AWARDED", today.plusDays(2), null, today, 7)).isFalse();
        assertThat(RfqReminderRules.quotationExpiryNoticeDue("WITHDRAWN", "CLOSED", today.plusDays(2), null, today, 7)).isFalse();
        assertThat(RfqReminderRules.quotationExpiryNoticeDue("SUBMITTED", "CLOSED", today.plusDays(2), now, today, 7)).as("already notified").isFalse();
        assertThat(RfqReminderRules.quotationExpiryNoticeDue("SUBMITTED", "CLOSED", null, null, today, 7)).isFalse();
    }
}
