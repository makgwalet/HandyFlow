// security/infrastructure/WebhookRetryScheduler.java
package za.co.handyflow.platform.security.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.security.application.internal.PublicApiService;
import za.co.handyflow.platform.security.domain.model.WebhookDelivery;
import za.co.handyflow.platform.security.domain.repository.WebhookDeliveryRepository;

import java.time.Instant;
import java.util.List;

/**
 * WebhookRetryScheduler — retries failed webhook deliveries every 5 minutes.
 *
 * Picks up all WebhookDelivery rows where:
 *   - delivered_at IS NULL (not yet successfully delivered)
 *   - next_retry_at <= NOW() (due for retry)
 *   - attempt_number < 5 (not exhausted — max 5 attempts total)
 *
 * Backoff schedule (set by PublicApiService.nextRetryAt):
 *   Attempt 1 → wait 1 min → Attempt 2 → wait 5 min → Attempt 3 → wait 15 min
 *   → Attempt 4 → wait 30 min → Attempt 5 → wait 60 min → give up
 *
 * After 5 failures on the same subscription, WebhookSubscription.recordFailure()
 * suspends the subscription automatically (failureCount >= 10). A supervisor
 * must reactivate it via POST /api/v1/security/webhooks/{id}/reactivate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookRetryScheduler {

    private final WebhookDeliveryRepository deliveryRepository;
    private final PublicApiService          publicApiService;

    @Scheduled(fixedDelay = 5 * 60 * 1000)  // every 5 minutes
    public void retryFailedDeliveries() {
        List<WebhookDelivery> due = deliveryRepository.findDueForRetry(Instant.now());
        if (due.isEmpty()) return;

        log.info("[WebhookRetry] {} deliveries due for retry", due.size());
        for (WebhookDelivery delivery : due) {
            try {
                publicApiService.retryDelivery(delivery);
            } catch (Exception e) {
                log.error("[WebhookRetry] Retry failed deliveryId={} error={}",
                        delivery.getId(), e.getMessage());
            }
        }
    }
}
