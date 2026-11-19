package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The ONLY place that talks to the Chunk 3 delivery-event code.
 * MAP THIS: replace the body with a call to your MarketingDeliveryEventService ingest method,
 * passing provider, eventId (idempotency key), type, campaignContactId, email, occurredAt and reason.
 * Until then it throws, so webhooks return 500 and providers retry instead of losing events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderEventBridge {

    private final MarketingDeliveryEventService deliveryEvents;

    public void apply(ProviderEvent e) {
        // Example shape (adjust to the real signature):
        // deliveryEvents.ingest(e.provider(), e.eventId(), e.type(), e.campaignContactId(), e.email(), e.occurredAt(), e.reason());
        throw new UnsupportedOperationException(
                "ProviderEventBridge.apply is not mapped to MarketingDeliveryEventService yet (event " + e.eventId() + ")");
    }
}