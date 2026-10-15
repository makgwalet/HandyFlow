package za.co.handyflow.platform.auditor.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One external audit: the firm, the period under audit, the entities covered, and whether it is running.
 * PLANNED -> ACTIVE -> COMPLETED -> ARCHIVED. Auditors attached to it can get in only while it is ACTIVE.
 */
@Entity(name = "ExternalAuditEngagement")
@Table(name = "external_audit_engagements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalAuditEngagement {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "name", nullable = false) private String name;
    @Column(name = "firm_name", nullable = false) private String firmName;
    @Column(name = "period_from", nullable = false) private LocalDate periodFrom;
    @Column(name = "period_to", nullable = false) private LocalDate periodTo;
    @Column(name = "entities_in_scope", columnDefinition = "TEXT") private String entitiesInScope;
    @Column(name = "scope_note", columnDefinition = "TEXT") private String scopeNote;
    @Column(name = "status", nullable = false) private String status = "PLANNED";
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "completed_by") private UUID completedBy;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "archived_by") private UUID archivedBy;

    private static String required(String v, String what) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(what + " is required");
        return v.trim();
    }

    private static String optional(String v) { return v == null || v.isBlank() ? null : v.trim(); }

    public static ExternalAuditEngagement create(UUID tenantId, String name, String firmName, LocalDate from, LocalDate to,
                                                 String entitiesInScope, String scopeNote, UUID by) {
        ExternalAuditEngagement e = new ExternalAuditEngagement();
        e.tenantId = tenantId; e.createdBy = by;
        e.setDetails(name, firmName, from, to, entitiesInScope, scopeNote);
        return e;
    }

    private void setDetails(String name, String firmName, LocalDate from, LocalDate to, String entities, String scope) {
        this.name = required(name, "A name");
        this.firmName = required(firmName, "The audit firm");
        if (from == null || to == null) throw new IllegalArgumentException("The period under audit is required");
        if (to.isBefore(from)) throw new IllegalArgumentException("The period must end on or after the day it starts");
        this.periodFrom = from; this.periodTo = to;
        this.entitiesInScope = optional(entities); this.scopeNote = optional(scope);
    }

    /** Details can change until the engagement is archived. */
    public void update(String name, String firmName, LocalDate from, LocalDate to, String entities, String scope) {
        if (isArchived()) throw new IllegalStateException("An archived engagement cannot be changed");
        setDetails(name, firmName, from, to, entities, scope);
    }

    public void start() {
        if (!"PLANNED".equals(status)) throw new IllegalStateException("Only a planned engagement can be started. Current status: " + status);
        this.status = "ACTIVE"; this.startedAt = Instant.now();
    }

    public void complete(UUID by) {
        if (!"ACTIVE".equals(status)) throw new IllegalStateException("Only an active engagement can be completed. Current status: " + status);
        this.status = "COMPLETED"; this.completedAt = Instant.now(); this.completedBy = by;
    }

    public void archive(UUID by) {
        if (!"COMPLETED".equals(status)) throw new IllegalStateException("Complete the engagement before archiving it. Current status: " + status);
        this.status = "ARCHIVED"; this.archivedAt = Instant.now(); this.archivedBy = by;
    }

    public boolean isArchived() { return "ARCHIVED".equals(status); }

    /** Auditors attached to this engagement can reach records only while this is true. */
    public boolean grantsAccess() { return "ACTIVE".equals(status); }

    /** New auditors can be invited until the engagement has finished. */
    public boolean acceptsInvites() { return "PLANNED".equals(status) || "ACTIVE".equals(status); }
}
