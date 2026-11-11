package za.co.handyflow.platform.events.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.events.application.internal.PaymentDecider.Action;
import za.co.handyflow.platform.events.application.internal.payment.PaymentGateway.Outcome;
import za.co.handyflow.platform.events.application.internal.payment.PaymentGateway.PaymentNotification;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static za.co.handyflow.platform.events.domain.model.EventPayment.*;

/**
 * The decision table is the heart of safe payment handling: providers retry, deliver late and out of
 * order, so every outcome can land on a payment in any state. These tests pin every cell of the table,
 * and then check the two properties that matter most hold for EVERY combination.
 */
class PaymentDeciderTest {

    // ── Money arrives on a payment that is still open ─────────────────────────

    @Test
    void paid_onPendingPayment_withTheRightAmount_confirmsTheTicket() {
        assertThat(PaymentDecider.decide(PENDING, Outcome.PAID, true, false)).isEqualTo(Action.CONFIRM);
    }

    @Test
    void paid_onPendingPayment_withTheWrongAmount_issuesNothingAndOwesARefund() {
        assertThat(PaymentDecider.decide(PENDING, Outcome.PAID, false, false))
                .isEqualTo(Action.REQUIRE_REFUND_AMOUNT_MISMATCH);
    }

    // ── Money arrives late ────────────────────────────────────────────────────

    @Test
    void paid_afterExpiry_reinstatesWhenTheSeatCanStillBeSecured() {
        assertThat(PaymentDecider.decide(EXPIRED, Outcome.PAID, true, true)).isEqualTo(Action.REINSTATE);
        assertThat(PaymentDecider.decide(FAILED, Outcome.PAID, true, true)).isEqualTo(Action.REINSTATE);
    }

    @Test
    void paid_afterExpiry_owesARefundWhenNoSeatIsLeft() {
        assertThat(PaymentDecider.decide(EXPIRED, Outcome.PAID, true, false)).isEqualTo(Action.REQUIRE_REFUND_NO_SEAT);
        assertThat(PaymentDecider.decide(FAILED, Outcome.PAID, true, false)).isEqualTo(Action.REQUIRE_REFUND_NO_SEAT);
    }

    @Test
    void paid_afterStaffCancelled_isNeverReinstated_evenIfASeatIsFree() {
        // Staff cancelled that reservation on purpose; a late payment must not quietly undo the decision.
        assertThat(PaymentDecider.decide(CANCELLED, Outcome.PAID, true, true)).isEqualTo(Action.REQUIRE_REFUND_NO_SEAT);
    }

    @Test
    void paid_late_withTheWrongAmount_isAMismatchRefund_notAReinstatement() {
        assertThat(PaymentDecider.decide(EXPIRED, Outcome.PAID, false, true))
                .isEqualTo(Action.REQUIRE_REFUND_AMOUNT_MISMATCH);
    }

    // ── Repeats: never double-issue ───────────────────────────────────────────

    @Test
    void paid_onAnAlreadySettledPayment_isIgnored_evenWhenTheAmountLooksWrong() {
        assertThat(PaymentDecider.decide(PAID, Outcome.PAID, true, false)).isEqualTo(Action.IGNORE_DUPLICATE);
        assertThat(PaymentDecider.decide(PAID, Outcome.PAID, false, false)).isEqualTo(Action.IGNORE_DUPLICATE);
        assertThat(PaymentDecider.decide(REQUIRES_REFUND, Outcome.PAID, true, true)).isEqualTo(Action.IGNORE_DUPLICATE);
    }

    // ── Failures ──────────────────────────────────────────────────────────────

    @Test
    void failed_releasesTheHoldOnlyWhileThePaymentIsStillPending() {
        assertThat(PaymentDecider.decide(PENDING, Outcome.FAILED, false, false)).isEqualTo(Action.FAIL);
    }

    @Test
    void failed_neverUndoesMoneyWeAlreadyHold_orReopensAClosedPayment() {
        for (String status : List.of(PAID, REQUIRES_REFUND, EXPIRED, FAILED, CANCELLED)) {
            assertThat(PaymentDecider.decide(status, Outcome.FAILED, false, false))
                    .as("FAILED on a %s payment", status).isEqualTo(Action.IGNORE);
        }
    }

    // ── Properties that must hold for EVERY combination ───────────────────────

    @Test
    void whenMoneyIsAlreadyHeld_aRepeatNotificationNeverIssuesAnotherTicket() {
        for (String status : List.of(PAID, REQUIRES_REFUND)) {
            for (boolean matches : new boolean[]{true, false}) {
                for (boolean canReinstate : new boolean[]{true, false}) {
                    Action action = PaymentDecider.decide(status, Outcome.PAID, matches, canReinstate);
                    assertThat(action).as("%s / matches=%s / canReinstate=%s", status, matches, canReinstate)
                            .isEqualTo(Action.IGNORE_DUPLICATE);
                }
            }
        }
    }

    @Test
    void aTicketIsOnlyEverIssued_forTheCorrectAmount() {
        for (String status : List.of(PENDING, PAID, FAILED, EXPIRED, CANCELLED, REQUIRES_REFUND)) {
            for (boolean canReinstate : new boolean[]{true, false}) {
                Action action = PaymentDecider.decide(status, Outcome.PAID, false, canReinstate);
                assertThat(action).as("wrong amount on %s", status)
                        .isNotIn(Action.CONFIRM, Action.REINSTATE);
            }
        }
    }

    // ── The notification contract the adapters must honour ────────────────────

    @Test
    void aPaidNotification_mustCarryAPositiveAmount() {
        UUID payment = UUID.randomUUID();
        assertThatThrownBy(() -> new PaymentNotification("evt-1", payment, Outcome.PAID, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentNotification("evt-1", payment, Outcome.PAID, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNotification_mustHaveAStableIdempotencyKeyAndAPaymentId() {
        UUID payment = UUID.randomUUID();
        assertThatThrownBy(() -> new PaymentNotification(" ", payment, Outcome.FAILED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentNotification("evt-1", null, Outcome.FAILED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // A FAILED notification legitimately carries no amount.
        assertThat(new PaymentNotification("evt-1", payment, Outcome.FAILED, null, null).outcome())
                .isEqualTo(Outcome.FAILED);
    }
}