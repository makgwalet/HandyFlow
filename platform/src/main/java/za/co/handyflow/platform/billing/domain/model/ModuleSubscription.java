package za.co.handyflow.platform.billing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "module_subscriptions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ModuleSubscription {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "module_key", nullable = false)
    private String moduleKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModuleStatus status;

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public enum ModuleStatus { ACTIVE, CANCELLED }

    public static ModuleSubscription activate(TenantId tenantId,
                                              String moduleKey,
                                              int priceCents) {
        ModuleSubscription ms = new ModuleSubscription();
        ms.tenantId = tenantId;
        ms.moduleKey = moduleKey.toLowerCase();
        ms.status = ModuleStatus.ACTIVE;
        ms.priceCents = priceCents;
        ms.activatedAt = Instant.now();
        ms.createdAt = Instant.now();
        ms.updatedAt = Instant.now();
        return ms;
    }

    public void cancel() {
        this.status = ModuleStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == ModuleStatus.ACTIVE;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
