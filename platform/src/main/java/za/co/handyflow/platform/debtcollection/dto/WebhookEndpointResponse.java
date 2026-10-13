package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.WebhookEndpoint;
import za.co.handyflow.platform.debtcollection.domain.model.WebhookPurpose;

import java.time.Instant;
import java.util.UUID;

/** Never contains the token or secret — those are shown once, at creation/rotation. */
public record WebhookEndpointResponse(UUID id, String name, WebhookPurpose purpose, int secretVersion, boolean active,
                                      Instant lastReceivedAt, Instant revokedAt, Instant createdAt) {
    public static WebhookEndpointResponse of(WebhookEndpoint e) {
        return new WebhookEndpointResponse(e.getId(), e.getName(), e.getPurpose(), e.getSecretVersion(), e.isActive(),
                e.getLastReceivedAt(), e.getRevokedAt(), e.getCreatedAt());
    }
}
