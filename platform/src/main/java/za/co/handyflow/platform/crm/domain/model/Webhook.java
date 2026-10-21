package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** An address that is told, with a signed message, when CRM events happen. */
@Entity
@Table(name = "crm_webhooks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Webhook {
    public static final int SUSPEND_AFTER = 10;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "secret", nullable = false, length = 80)
    private String secret;

    @Column(name = "events", nullable = false, length = 200)
    private String events;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "last_delivery_at")
    private Instant lastDeliveryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Webhook create(TenantId tenantId, String name, String url, String secret, String events, boolean active) {
        var w = new Webhook();
        w.tenantId = tenantId;
        w.secret = secret;
        w.createdAt = Instant.now();
        w.change(name, url, events, active);
        return w;
    }

    /** Saving a webhook again is how a person says "try again": it clears the failure count and any suspension. */
    public void change(String name, String url, String events, boolean active) {
        this.name = name.strip();
        this.url = url.strip();
        this.events = events;
        this.active = active;
        this.consecutiveFailures = 0;
        this.suspendedAt = null;
        this.updatedAt = Instant.now();
    }

    public boolean isSuspended() { return suspendedAt != null; }

    public void recordResult(boolean ok) {
        this.lastDeliveryAt = Instant.now();
        if (ok) {
            this.consecutiveFailures = 0;
        } else if (++this.consecutiveFailures >= SUSPEND_AFTER && this.suspendedAt == null) {
            this.suspendedAt = this.lastDeliveryAt;
        }
    }

    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = this.deletedAt; }
}
