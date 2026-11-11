package za.co.handyflow.platform.events.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.events.application.internal.PaymentLedgerService;
import za.co.handyflow.platform.events.application.internal.payment.PaymentGateway;
import za.co.handyflow.platform.events.application.internal.payment.PaymentGateways;
import za.co.handyflow.platform.events.application.internal.payment.PaymentVerificationException;
import za.co.handyflow.platform.shared.RateLimiter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Receives server-to-server payment notifications from an online provider.
 * <p>
 * <b>SECURITY CONFIG — REQUIRED:</b> the provider's server is not a logged-in user, so this path must
 * be permitted without a JWT: {@code /api/v1/events/payments/webhook/**} (and exempted from CSRF if
 * that is enabled). That is a deliberate hole, which is why nothing here trusts the request itself:
 * <ul>
 *   <li>the adapter must PROVE the call is genuine (signature, merchant id, server-side confirmation
 *       with the provider) before it returns anything — a forged call is rejected with 400 and
 *       nothing is processed;</li>
 *   <li>only providers that declare they send notifications are reachable at all — an unknown name,
 *       or MANUAL, gets 404;</li>
 *   <li>calls are rate-limited per client address;</li>
 *   <li>processing is idempotent, so replaying a genuine notification changes nothing.</li>
 * </ul>
 * Answers 200 for everything that was authentic — including duplicates, unknown payments and no-ops —
 * so the provider stops retrying. Only a failed verification (400) or throttling (429) invites a retry.
 * <p>
 * Inert until an adapter whose {@code supportsNotifications()} is true is installed.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/api/v1/events/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private static final int MAX_CALLS_PER_MINUTE = 300;
    private static final long WINDOW_MS = 60_000;

    private final PaymentGateways      gateways;
    private final PaymentLedgerService ledger;
    private final RateLimiter          rateLimiter;

    @PostMapping("/{provider}")
    public ResponseEntity<Void> receive(@PathVariable String provider,
                                        @RequestBody String body,
                                        @RequestHeader Map<String, String> requestHeaders,
                                        HttpServletRequest request) {
        Optional<PaymentGateway> gateway = gateways.find(provider).filter(PaymentGateway::supportsNotifications);
        if (gateway.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Note: getRemoteAddr() is the proxy's address if the app sits behind one. That still bounds the
        // total load this endpoint accepts; it just can't tell individual callers apart.
        if (!rateLimiter.tryConsume("events:payment-webhook:" + request.getRemoteAddr(),
                MAX_CALLS_PER_MINUTE, WINDOW_MS)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(requestHeaders);

        Optional<PaymentGateway.PaymentNotification> notification;
        try {
            notification = gateway.get().parseNotification(headers, body);
        } catch (PaymentVerificationException | IllegalArgumentException e) {
            // Never log the body: it can carry payer details.
            log.warn("[Events] Rejected payment notification from {}: {}", provider, e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        if (notification.isEmpty()) {
            return ResponseEntity.ok().build();       // a ping, or an event type we don't act on
        }

        PaymentLedgerService.NotificationOutcome outcome =
                ledger.processNotification(gateway.get().provider(), notification.get(), sha256(body));
        log.info("[Events] Payment notification processed provider={} outcome={}", provider, outcome);
        return ResponseEntity.ok().build();
    }

    private static String sha256(String body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}