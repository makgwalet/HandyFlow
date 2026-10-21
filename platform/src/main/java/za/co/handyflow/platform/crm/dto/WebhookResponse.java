package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.WebhookEvent;

/** suspended = stopped after repeated failures; saving the webhook again turns it back on. */
public record WebhookResponse(UUID id, String name, String url, List<WebhookEvent> events, boolean active, boolean suspended, int consecutiveFailures, Instant lastDeliveryAt) {}
