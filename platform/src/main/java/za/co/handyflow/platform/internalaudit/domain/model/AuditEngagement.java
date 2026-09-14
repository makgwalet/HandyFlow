package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One specific instance of auditing one universe entry. planEntryId is
 * nullable — an engagement can be ad-hoc (fraud tip-off, board
 * request), not only plan-driven, matching real internal audit
 * practice. Planning-detail fields (objectives, scope, materiality,
 * audit criteria) and the full materiality model are Phase 2, per the
 * agreed phased build — deliberately not on this entity yet, so this
 * stays a genuine "shell" a later phase extends rather than a
 * half-built planning record.
 */
@Entity
@Table(name = "audit_engagements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEngagement {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "plan_entry_id") private UUID planEntryId;
    @Column(name = "universe_entry_id", nullable = false) private UUID universeEntryId;
    @Column(name = "name", nullable = false) private String name;
    @Column(name = "status", nullable = false) private String status = "PLANNING"; // PLANNING | FIELDWORK | REPORTING | CLOSED
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static AuditEngagement create(UUID tenantId, UUID planEntryId, UUID universeEntryId,
                                         String name, LocalDate startDate, LocalDate endDate, UUID createdBy) {
        AuditEngagement e = new AuditEngagement();
        e.tenantId = tenantId;
        e.planEntryId = planEntryId;
        e.universeEntryId = universeEntryId;
        e.name = name;
        e.startDate = startDate;
        e.endDate = endDate;
        e.createdBy = createdBy;
        e.createdAt = Instant.now();
        return e;
    }

    public void advanceStatus(String status) {
        this.status = status;
    }
}
