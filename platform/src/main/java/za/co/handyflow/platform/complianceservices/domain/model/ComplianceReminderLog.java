package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** What happened to one person's digest on one day. At most one a day. "Handed over" is not "read". */
@Entity(name = "ComplianceReminderLog")
@Table(name = "compliance_reminder_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceReminderLog {
    public static final Set<String> OUTCOMES = Set.of("HANDED_OVER", "NOTHING_DUE", "NO_ADDRESS", "FAILED");

    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;
    @Column(name = "item_count", nullable = false)
    private int itemCount;
    @Column(nullable = false)
    private String outcome;
    private String detail;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static ComplianceReminderLog of(TenantId tenantId, UUID userId, LocalDate runDate, int itemCount, String outcome, String detail) {
        if (!OUTCOMES.contains(outcome)) throw new IllegalArgumentException("Unknown outcome");
        ComplianceReminderLog l = new ComplianceReminderLog();
        l.tenantId = tenantId;
        l.userId = userId;
        l.runDate = runDate;
        l.itemCount = Math.max(0, itemCount);
        l.outcome = outcome;
        l.detail = detail == null ? null : detail.length() > 500 ? detail.substring(0, 500) : detail;
        l.createdAt = Instant.now();
        return l;
    }
}
