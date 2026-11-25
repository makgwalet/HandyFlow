package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Hours worked for a client. Never edited or deleted: a mistake is voided with a reason and entered again. */
@Entity(name = "ComplianceTimeEntry")
@Table(name = "compliance_time_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceTimeEntry {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;
    @Column(nullable = false)
    private int minutes;
    @Column(name = "service_code")
    private String serviceCode;
    @Column(nullable = false)
    private String kind = "PLANNED";
    @Column(nullable = false)
    private boolean billable = true;
    private String note;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "voided_by")
    private UUID voidedBy;
    @Column(name = "voided_at")
    private Instant voidedAt;
    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;
    @Version
    private Long version;

    public static ComplianceTimeEntry record(TenantId tenantId, UUID clientId, UUID userId, LocalDate workDate, int minutes, String serviceCode, String kind,
                                             boolean billable, String note, LocalDate today, boolean admin, int alreadyThatDay) {
        if (userId == null) throw new IllegalArgumentException("Time is recorded by a person");
        TimeRules.check(minutes, workDate, today, admin, alreadyThatDay);
        ComplianceTimeEntry e = new ComplianceTimeEntry();
        e.tenantId = tenantId;
        e.clientId = clientId;
        e.userId = userId;
        e.workDate = workDate;
        e.minutes = minutes;
        e.serviceCode = TimeRules.serviceCode(serviceCode);
        e.kind = TimeRules.kind(kind);
        e.billable = billable;
        e.note = TimeRules.note(note);
        e.createdAt = Instant.now();
        return e;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    public void voidIt(UUID by, String reason) {
        if (!isActive()) throw new IllegalStateException("This entry has already been voided");
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        this.voidReason = TimeRules.reason(reason);
        this.status = "VOID";
        this.voidedBy = by;
        this.voidedAt = Instant.now();
    }
}
