package za.co.handyflow.platform.marketing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A recipient address that must NOT receive marketing on a channel.
 * Never deleted — lifting sets liftedAt/liftedBy/liftReason so the history
 * (who lifted it, why) stays auditable.
 */
@Entity
@Table(name = "mkt_suppression_entries")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MktSuppressionEntry {

    public static final String REASON_UNSUBSCRIBED = "UNSUBSCRIBED";
    public static final String REASON_COMPLAINT    = "COMPLAINT";
    public static final String REASON_HARD_BOUNCE  = "HARD_BOUNCE";
    public static final String REASON_MANUAL       = "MANUAL";

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 320) private String email;
    @Column(nullable = false, length = 20)  private String channel = "EMAIL";
    @Column(nullable = false, length = 30)  private String reason;
    @Column(length = 50)                    private String source;
    @Column(columnDefinition = "text")      private String note;

    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @Column(name = "lifted_at")  private Instant liftedAt;
    @Column(name = "lifted_by")  private UUID    liftedBy;
    @Column(name = "lift_reason", columnDefinition = "text") private String liftReason;

    public boolean isActive() { return liftedAt == null; }

    public void lift(UUID by, String reason) {
        this.liftedAt   = Instant.now();
        this.liftedBy   = by;
        this.liftReason = reason;
    }
}