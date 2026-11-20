package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One change to whether matters and trust receipts are blocked until the client is verified. Append-only; the latest row is in force; no rows means not enforced. */
@Entity
@Table(name = "lp_kyc_setting_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpKycSettingChange {

    public static final int MIN_REASON = 10;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private boolean enforce;

    @Column(nullable = false)
    private String reason;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_by_name")
    private String changedByName;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public static LpKycSettingChange record(TenantId tenantId, boolean enforce, String reason, UUID by, String byName) {
        if (reason == null || reason.trim().length() < MIN_REASON) throw new IllegalArgumentException("Give a reason of at least " + MIN_REASON + " characters");
        if (by == null) throw new IllegalArgumentException("A change must say who made it");
        LpKycSettingChange c = new LpKycSettingChange();
        c.tenantId = tenantId;
        c.enforce = enforce;
        c.reason = reason.trim();
        c.changedBy = by;
        c.changedByName = byName;
        c.changedAt = Instant.now();
        return c;
    }
}
