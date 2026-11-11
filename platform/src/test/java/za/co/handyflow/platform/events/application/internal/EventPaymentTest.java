package za.co.handyflow.platform.events.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.events.domain.model.EventPayment;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pins the payment state machine and the refund arithmetic — the rules that stop money going wrong. */
class EventPaymentTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

    private EventPayment pending(String amount) {
        return EventPayment.pending(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal(amount), "MANUAL", NOW.plusSeconds(900));
    }

    // ── Creation ──────────────────────────────────────────────────────────────

    @Test
    void aPaymentMustBeForAPositiveAmount() {
        assertThatThrownBy(() -> pending("0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending("-50")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNewPayment_isPending_andHoldsNoMoney() {
        EventPayment p = pending("500");
        assertThat(p.isPending()).isTrue();
        assertThat(p.isMoneyHeld()).isFalse();
        assertThat(p.refundable()).isEqualByComparingTo("0");
    }

    // ── Receiving money ───────────────────────────────────────────────────────

    @Test
    void markPaid_recordsWhatActuallyArrived_andThatIsWhatBecomesRefundable() {
        EventPayment p = pending("500");
        p.markPaid(NOW, new BigDecimal("500"), new BigDecimal("12.50"));

        assertThat(p.getStatus()).isEqualTo(EventPayment.PAID);
        assertThat(p.getReceivedAmount()).isEqualByComparingTo("500");
        assertThat(p.getFeeAmount()).isEqualByComparingTo("12.50");
        assertThat(p.refundable()).isEqualByComparingTo("500");
        assertThat(p.isMoneyHeld()).isTrue();
    }

    @Test
    void aMissingFee_isTreatedAsZero() {
        EventPayment p = pending("500");
        p.markPaid(NOW, new BigDecimal("500"), null);
        assertThat(p.getFeeAmount()).isEqualByComparingTo("0");
    }

    @Test
    void aPaymentCanArriveLate_afterExpiry_failure_orCancellation() {
        for (String how : new String[]{"expire", "fail", "cancel"}) {
            EventPayment p = pending("500");
            switch (how) {
                case "expire" -> p.expire();
                case "fail" -> p.fail("declined");
                default -> p.cancel("staff cancelled");
            }
            p.markPaid(NOW, new BigDecimal("500"), null);
            assertThat(p.getStatus()).as("late payment after %s", how).isEqualTo(EventPayment.PAID);
        }
    }

    @Test
    void aPaidPayment_cannotBeMarkedPaidAgain() {
        EventPayment p = pending("500");
        p.markPaid(NOW, new BigDecimal("500"), null);
        assertThatThrownBy(() -> p.markPaid(NOW, new BigDecimal("500"), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void receivedButUnhonoured_refundsWhatWasReceived_notWhatWasAskedFor() {
        EventPayment p = pending("500");
        p.receivedButUnhonoured(NOW, new BigDecimal("450"), null, "Received R450.00 but R500.00 was due");

        assertThat(p.getStatus()).isEqualTo(EventPayment.REQUIRES_REFUND);
        assertThat(p.refundable()).isEqualByComparingTo("450");   // the short payment, not the 500 price
        assertThat(p.getReviewReason()).contains("R450.00");
    }

    // ── Leaving PENDING ───────────────────────────────────────────────────────

    @Test
    void failExpireAndCancel_workOnlyFromPending() {
        EventPayment paid = pending("500");
        paid.markPaid(NOW, new BigDecimal("500"), null);

        assertThatThrownBy(() -> paid.fail("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(paid::expire).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> paid.cancel("x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void providerReference_canOnlyBeAttachedWhilePending() {
        EventPayment p = pending("500");
        p.attachProviderRef("REF-1");
        assertThat(p.getProviderRef()).isEqualTo("REF-1");

        p.expire();
        assertThatThrownBy(() -> p.attachProviderRef("REF-2")).isInstanceOf(IllegalStateException.class);
    }

    // ── Cancelled after paying ────────────────────────────────────────────────

    @Test
    void cancellingAPaidTicket_flagsARefundDue_andKeepsTheMoneyHeld() {
        EventPayment p = pending("500");
        p.markPaid(NOW, new BigDecimal("500"), null);
        p.flagRefundDue("Registration cancelled");

        assertThat(p.getStatus()).isEqualTo(EventPayment.REQUIRES_REFUND);
        assertThat(p.refundable()).isEqualByComparingTo("500");
    }

    @Test
    void flagRefundDue_isOnlyForPaidPayments() {
        EventPayment p = pending("500");
        assertThatThrownBy(() -> p.flagRefundDue("x")).isInstanceOf(IllegalStateException.class);
    }

    // ── Refund arithmetic ─────────────────────────────────────────────────────

    @Test
    void partialRefunds_accumulate_andReduceWhatIsStillRefundable() {
        EventPayment p = pending("500");
        p.markPaid(NOW, new BigDecimal("500"), null);

        p.applyRefund(new BigDecimal("150"));
        assertThat(p.getRefundedAmount()).isEqualByComparingTo("150");
        assertThat(p.refundable()).isEqualByComparingTo("350");

        p.applyRefund(new BigDecimal("350"));
        assertThat(p.refundable()).isEqualByComparingTo("0");
    }

    @Test
    void aRefund_cannotExceedWhatIsStillRefundable() {
        EventPayment p = pending("500");
        p.markPaid(NOW, new BigDecimal("500"), null);
        p.applyRefund(new BigDecimal("400"));

        assertThatThrownBy(() -> p.applyRefund(new BigDecimal("150")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(p.getRefundedAmount()).isEqualByComparingTo("400");   // unchanged by the failed attempt
    }

    @Test
    void aRefund_mustBePositive_andNeedsMoneyToBeHeld() {
        EventPayment p = pending("500");
        assertThatThrownBy(() -> p.applyRefund(new BigDecimal("10"))).isInstanceOf(IllegalArgumentException.class); // nothing held

        p.markPaid(NOW, new BigDecimal("500"), null);
        assertThatThrownBy(() -> p.applyRefund(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.applyRefund(new BigDecimal("-5"))).isInstanceOf(IllegalArgumentException.class);
    }
}