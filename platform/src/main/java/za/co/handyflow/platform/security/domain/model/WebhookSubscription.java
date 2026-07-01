// security/domain/model/WebhookSubscription.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_webhook_subscriptions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WebhookSubscription {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 100) private String name;
    @Column(name = "endpoint_url", nullable = false) private String endpointUrl;
    @Column(name = "signing_secret", nullable = false, length = 100) private String signingSecret;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "event_types", nullable = false, columnDefinition = "jsonb")
    private String eventTypes;  // JSON array: ["ALARM_EVENT","SHIFT_MISSED"]

    @Column(name = "branch_id") private UUID branchId;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "failure_count", nullable = false) private int failureCount = 0;
    @Column(name = "suspended_at") private Instant suspendedAt;
    @Column(name = "last_success_at") private Instant lastSuccessAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    private static final int MAX_FAILURES_BEFORE_SUSPEND = 10;

    public static WebhookSubscription create(TenantId tenantId, String name, String endpointUrl,
                                             String signingSecret, String eventTypes,
                                             UUID branchId, UUID createdBy) {
        WebhookSubscription ws = new WebhookSubscription();
        ws.tenantId = tenantId; ws.name = name.strip(); ws.endpointUrl = endpointUrl;
        ws.signingSecret = signingSecret; ws.eventTypes = eventTypes; ws.branchId = branchId;
        ws.createdBy = createdBy; ws.active = true; ws.failureCount = 0;
        ws.createdAt = Instant.now(); ws.updatedAt = Instant.now();
        return ws;
    }

    public void recordSuccess() {
        this.failureCount = 0; this.lastSuccessAt = Instant.now(); this.updatedAt = Instant.now();
    }

    public void recordFailure() {
        this.failureCount++;
        if (this.failureCount >= MAX_FAILURES_BEFORE_SUSPEND) {
            this.active = false; this.suspendedAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.active = true; this.failureCount = 0; this.suspendedAt = null;
        this.updatedAt = Instant.now();
    }

    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }
    public boolean isSuspended() { return suspendedAt != null && !active; }
}