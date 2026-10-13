package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant's inbound webhook endpoint. The URL carries a random token (only its SHA-256 is stored) that selects the
 * tenant and purpose; the sender proves it is genuine by signing each request with a secret. The secret is never
 * stored: it is derived from a server-side master key (HMAC of endpoint id + secretVersion), shown once when the
 * endpoint is created or rotated, and recomputed to verify requests. Rotating bumps secretVersion; revoking
 * deactivates the endpoint (the row stays for audit).
 */
@Entity
@Table(name = "debtcollection_webhook_endpoints")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEndpoint extends AggregateRoot<WebhookEndpoint> {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookPurpose purpose;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "secret_version", nullable = false)
    private int secretVersion;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "last_received_at")
    private Instant lastReceivedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static WebhookEndpoint create(TenantId tenantId, String name, WebhookPurpose purpose, String tokenHash, UUID createdBy) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (purpose == null) throw new IllegalArgumentException("purpose is required");
        WebhookEndpoint e = new WebhookEndpoint();
        e.initTenantId(tenantId);
        e.name = name;
        e.purpose = purpose;
        e.tokenHash = tokenHash;
        e.secretVersion = 1;
        e.active = true;
        e.createdBy = createdBy;
        return e;
    }

    public void rotateSecret() {
        if (!active) throw new IllegalStateException("Cannot rotate the secret of a revoked endpoint");
        this.secretVersion++;
    }

    public void revoke() {
        if (!active) throw new IllegalStateException("Endpoint is already revoked");
        this.active = false;
        this.revokedAt = Instant.now();
    }

    public void markReceived() {
        this.lastReceivedAt = Instant.now();
    }
}
