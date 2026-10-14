package za.co.handyflow.platform.auditor.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One decision to show an item to external auditors, and, later, to stop. History stays; an item has at most one live share. */
@Entity(name = "AuditShare")
@Table(name = "audit_shares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditShare {
    public static final String EVIDENCE = "EVIDENCE";
    public static final String CONTROL_EXCEPTION = "CONTROL_EXCEPTION";
    public static final String FINDING = "FINDING";

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "item_type", nullable = false) private String itemType;
    @Column(name = "item_id", nullable = false) private UUID itemId;
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT") private String reason;
    @Column(name = "shared_by", nullable = false) private UUID sharedBy;
    @Column(name = "shared_by_name") private String sharedByName;
    @Column(name = "shared_at", nullable = false) private Instant sharedAt = Instant.now();
    @Column(name = "withdrawn_by") private UUID withdrawnBy;
    @Column(name = "withdrawn_by_name") private String withdrawnByName;
    @Column(name = "withdrawn_at") private Instant withdrawnAt;
    @Column(name = "withdrawn_reason", columnDefinition = "TEXT") private String withdrawnReason;

    public static AuditShare share(UUID tenantId, String itemType, UUID itemId, String reason, UUID by, String byName) {
        if (!java.util.Set.of(EVIDENCE, CONTROL_EXCEPTION, FINDING).contains(itemType)) throw new IllegalArgumentException("Unknown item type " + itemType);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason for sharing is required");
        AuditShare s = new AuditShare();
        s.tenantId = tenantId; s.itemType = itemType; s.itemId = itemId; s.reason = reason.trim(); s.sharedBy = by; s.sharedByName = byName;
        return s;
    }

    public boolean isLive() { return withdrawnAt == null; }

    public void withdraw(UUID by, String byName, String why) {
        if (!isLive()) throw new IllegalStateException("This share has already been withdrawn");
        if (why == null || why.isBlank()) throw new IllegalArgumentException("A reason for withdrawing is required");
        this.withdrawnBy = by; this.withdrawnByName = byName; this.withdrawnAt = Instant.now(); this.withdrawnReason = why.trim();
    }
}
