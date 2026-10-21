package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** "Send this report to these people on this schedule." No recipients means the account's admins. */
@Entity
@Table(name = "crm_report_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportSchedule {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 20)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 10)
    private ReportFrequency frequency;

    @Column(name = "recipient_ids", nullable = false)
    private String recipientIds = "";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static ReportSchedule create(TenantId tenantId, String name, ReportType type, ReportFrequency frequency, String recipientIds, boolean active) {
        var s = new ReportSchedule();
        s.tenantId = tenantId;
        s.createdAt = Instant.now();
        s.change(name, type, frequency, recipientIds, active);
        return s;
    }

    public void change(String name, ReportType type, ReportFrequency frequency, String recipientIds, boolean active) {
        this.name = name.strip();
        this.reportType = type;
        this.frequency = frequency;
        this.recipientIds = recipientIds == null ? "" : recipientIds;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void markSent() { this.lastSentAt = Instant.now(); }

    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = this.deletedAt; }
}
