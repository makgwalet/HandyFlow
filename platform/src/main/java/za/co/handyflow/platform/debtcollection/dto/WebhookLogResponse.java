package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.WebhookDeliveryLog;

import java.time.Instant;
import java.util.UUID;

public record WebhookLogResponse(UUID id, String outcome, int httpStatus, String detail, Instant createdAt) {
    public static WebhookLogResponse of(WebhookDeliveryLog l) {
        return new WebhookLogResponse(l.getId(), l.getOutcome(), l.getHttpStatus(), l.getDetail(), l.getCreatedAt());
    }
}
