package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.events.domain.repository.EventPaymentRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Releases seats held for checkouts that were never paid. Runs every minute by default.
 * <p>
 * Each expiry is its own transaction (PaymentLedgerService.expireOne is called through its
 * proxy), so one bad payment can't block the others, and a crash part-way leaves nothing
 * half-done. Running on several app instances at once is safe: expireOne re-checks under the
 * event lock that the payment is still pending and still overdue, so a second run finds
 * nothing to do. Switch off with app.events.payments.expiry-enabled=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private static final int BATCH = 100;

    private final EventPaymentRepository paymentRepo;
    private final PaymentLedgerService   ledger;

    @Value("${app.events.payments.expiry-enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.events.payments.expiry-sweep-ms:60000}")
    public void releaseExpiredReservations() {
        if (!enabled) return;

        List<UUID> due = paymentRepo.findExpiredPendingIds(Instant.now(), PageRequest.of(0, BATCH));
        int released = 0;
        for (UUID paymentId : due) {
            try {
                if (ledger.expireOne(paymentId)) released++;
            } catch (Exception e) {
                // Logged and skipped — it stays PENDING and the next sweep tries it again.
                log.error("[Events] Failed to expire reservation payment={}: {}", paymentId, e.getMessage(), e);
            }
        }
        if (released > 0) {
            log.info("[Events] Released {} expired reservation(s)", released);
        }
    }
}