package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * The ONLY place that talks to {@link MarketingDeliveryEventService} on behalf of the SES / SendGrid parsers.
 *
 * Idempotency, tenant resolution (from OUR campaign-contact row, never the event body), forward-only status
 * and the deliverability circuit breaker all live in {@link MarketingDeliveryEventService#ingest}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderEventBridge {

    private static final Set<String> SUPPORTED = Set.of("DELIVERED", "HARD_BOUNCE", "COMPLAINT");

    private final MarketingDeliveryEventService deliveryEvents;

    /** @return the ingest outcome; unsupported event types are IGNORED rather than failing the webhook. */
    public MarketingDeliveryEventService.IngestResult apply(ProviderEvent e) {
        if (e == null || e.type() == null || !SUPPORTED.contains(e.type())) {
            return MarketingDeliveryEventService.IngestResult.IGNORED;
        }
        var result = deliveryEvents.ingest(e.provider(), new MarketingDeliveryEventService.ParsedEvent(
                e.eventId(), e.type(), e.campaignContactId(), e.email(), e.occurredAt(), e.reason()));
        log.debug("Provider event {} {} -> {}", e.provider(), e.eventId(), result);
        return result;
    }
}
