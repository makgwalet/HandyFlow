package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.WebhookEvent;

public record WebhookDeliveryResponse(UUID id, UUID webhookId, String webhookName, WebhookEvent event, String outcome, Integer statusCode, String detail, Instant sentAt) {}
