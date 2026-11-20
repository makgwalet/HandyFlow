package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * One change to the firm's trust control settings. Append-only: the setting in force is the latest row, and the rows
 * before it are the history of who relaxed or tightened the control, when, and why. A firm with no rows has the strict
 * default: two different people for every payment out of trust.
 */
@Entity
@Table(name = "lp_trust_setting_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpTrustSettingChange {

    public static final int MIN_REASON = 10;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    /** True lets one person request and approve their own trust payment (with a written reason each time). */
    @Column(name = "self_approval_allowed", nullable = false)
    private boolean selfApprovalAllowed;

    @Column(nullable = false)
    private String reason;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_by_name", length = 255)
    private String changedByName;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public static LpTrustSettingChange record(TenantId tenantId, boolean selfApprovalAllowed, String reason, UUID by, String byName) {
        if (reason == null || reason.trim().length() < MIN_REASON) {
            throw new IllegalArgumentException("Give a reason of at least " + MIN_REASON + " characters");
        }
        if (by == null) {
            throw new IllegalArgumentException("A change must say who made it");
        }
        LpTrustSettingChange c = new LpTrustSettingChange();
        c.tenantId = tenantId;
        c.selfApprovalAllowed = selfApprovalAllowed;
        c.reason = reason.trim();
        c.changedBy = by;
        c.changedByName = byName;
        c.changedAt = Instant.now();
        return c;
    }
}
