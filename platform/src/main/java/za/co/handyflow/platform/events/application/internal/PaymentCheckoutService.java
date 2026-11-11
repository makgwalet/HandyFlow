package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.events.application.internal.payment.PaymentGateway;
import za.co.handyflow.platform.events.application.internal.payment.PaymentGateways;
import za.co.handyflow.platform.events.dto.CheckoutResponse;
import za.co.handyflow.platform.events.dto.StartCheckoutRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Starts a paid checkout in three steps, deliberately NOT one transaction:
 * <ol>
 *   <li>reserve the seat and record a PENDING payment — short transaction, commits;</li>
 *   <li>ask the provider to open the payment — a network call made with NO database
 *       transaction and NO event lock held, so a slow provider can't stall registration;</li>
 *   <li>store the provider's reference — short transaction.</li>
 * </ol>
 * If step 2 fails the reservation is released immediately instead of tying up a seat for
 * the full hold. If the process dies between steps 1 and 3, the unpaid hold simply expires
 * through the normal sweep — there is no state that needs manual repair.
 * <p>
 * This class is intentionally not @Transactional.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCheckoutService {

    private static final int MIN_HOLD_MINUTES = 5;
    private static final int MAX_HOLD_MINUTES = 60;

    private final PaymentLedgerService ledger;
    private final PaymentGateways      gateways;

    /** How long an unpaid seat is held. Clamped so a typo can't hold seats for days or release them in seconds. */
    @Value("${app.events.payments.hold-minutes:15}")
    private int holdMinutes;

    public CheckoutResponse startCheckout(TenantId tenantId, UUID eventId, StartCheckoutRequest req) {
        PaymentGateway gateway = gateways.defaultGateway();
        int hold = Math.max(MIN_HOLD_MINUTES, Math.min(MAX_HOLD_MINUTES, holdMinutes));

        // Step 1 — commits before any provider call.
        PaymentLedgerService.Reserved reserved = ledger.reserve(tenantId, eventId, req, gateway.provider(), hold);
        UUID paymentId = reserved.payment().getId();

        // Step 2 — network call, outside any transaction.
        PaymentGateway.CheckoutSession session;
        try {
            session = gateway.createCheckout(new PaymentGateway.CheckoutRequest(
                    paymentId, reserved.payment().getAmount(), reserved.payment().getCurrency(),
                    "Ticket: " + reserved.eventTitle(), req.fullName(), req.email(),
                    reserved.payment().getExpiresAt()));
        } catch (RuntimeException e) {
            log.error("[Events] Provider {} could not start checkout for payment={}: {}",
                    gateway.provider(), paymentId, e.getMessage(), e);
            ledger.abandonReservation(paymentId, "Could not start the payment with the provider");
            throw new HandyFlowException("The payment provider is unavailable — please try again shortly",
                    HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_ERROR");
        }

        // Step 3.
        ledger.attachProviderRef(paymentId, session.providerRef());

        return new CheckoutResponse(paymentId, reserved.guest().getId(), reserved.guest().getTicketNumber(),
                reserved.payment().getAmount(), reserved.payment().getCurrency(), gateway.provider(),
                reserved.payment().getExpiresAt(), session.redirectUrl(), session.instructions());
    }
}