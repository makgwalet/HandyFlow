package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.projects.domain.enums.ProjectHealth;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Core aggregate root for the Project Management module.
 *
 * CHUNK 1 CHANGES
 * ───────────────
 * - New governance fields: sponsor, objectives, scope statement, business case.
 * - New lifecycle states and methods: propose / approve / returnToPlanning /
 *   reopen / close. The schedule baseline is locked (snapshotted) on approval,
 *   or on first activation if the approval step is skipped.
 * - Approval / closure audit fields.
 * - @Version for optimistic locking — concurrent status changes, change-order
 *   approvals and task-driven health recalculation can no longer silently
 *   overwrite each other (the global handler maps the failure to HTTP 409).
 *
 * CHUNK 5: baselineVersion — which version of the approved baseline is current (see
 * project_baseline_revisions); applyApprovedSchedule() moves endDate and baselineEnd together.
 *
 * CHUNK 2 CHANGES
 * ───────────────
 * - Client-portal token lifecycle: portalEnabled flag, optional expiry,
 *   rotatePortalToken() (invalidates the old link), disablePortal()/enablePortal(),
 *   and isPortalAccessible() as the single rule deciding whether a token works.
 * - Setters for contractType / retentionPct / startDate, which the create and
 *   update requests carried but the service previously could not apply.
 */
@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor
public class Project {

    // ── Health threshold constants ────────────────────────────────────────────
    private static final BigDecimal BUDGET_RED_FACTOR   = new BigDecimal("1.10");
    private static final BigDecimal BUDGET_AMBER_FACTOR = new BigDecimal("1.05");
    private static final int        SCHEDULE_RED_DAYS   = 14;
    private static final int        SCHEDULE_AMBER_DAYS = 7;

    // ── Identity ──────────────────────────────────────────────────────────────
    @Id
    UUID id;

    @Version
    Long version;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "project_number", nullable = false)
    String projectNumber;

    // ── Core attributes ───────────────────────────────────────────────────────
    @Column(nullable = false)
    String name;

    String description;

    @Column(name = "project_type", nullable = false)
    String projectType = "GENERAL";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ProjectStatus status = ProjectStatus.PLANNING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ProjectHealth health = ProjectHealth.GREEN;

    // ── Governance ────────────────────────────────────────────────────────────
    @Column(name = "sponsor_id")      UUID   sponsorId;
    @Column(name = "sponsor_name")    String sponsorName;
    @Column(name = "objectives")      String objectives;
    @Column(name = "scope_statement") String scopeStatement;
    @Column(name = "business_case")   String businessCase;

    @Column(name = "proposed_at")      Instant proposedAt;
    @Column(name = "approved_at")      Instant approvedAt;
    @Column(name = "approved_by")      UUID    approvedBy;
    @Column(name = "approved_by_name") String  approvedByName;

    // ── Client ────────────────────────────────────────────────────────────────
    @Column(name = "client_id")   UUID   clientId;
    @Column(name = "client_name") String clientName;
    @Column(name = "site_address") String siteAddress;

    // ── Schedule ──────────────────────────────────────────────────────────────
    @Column(name = "start_date")     LocalDate startDate;
    @Column(name = "end_date")       LocalDate endDate;
    @Column(name = "baseline_start") LocalDate baselineStart;
    @Column(name = "baseline_end")   LocalDate baselineEnd;

    // ── Budget ────────────────────────────────────────────────────────────────
    @Column(name = "budget_total",     nullable = false) BigDecimal budgetTotal     = BigDecimal.ZERO;
    @Column(name = "budget_spent",     nullable = false) BigDecimal budgetSpent     = BigDecimal.ZERO;
    @Column(name = "budget_committed", nullable = false) BigDecimal budgetCommitted = BigDecimal.ZERO;

    // ── Contract ──────────────────────────────────────────────────────────────
    @Column(name = "contract_value") BigDecimal contractValue;
    @Column(name = "contract_ref")   String contractRef;
    @Column(name = "contract_type")  String contractType;
    @Column(name = "retention_pct")  BigDecimal retentionPct = BigDecimal.ZERO;

    // ── SA compliance ─────────────────────────────────────────────────────────
    @Column(name = "cidb_grade")   String cidbGrade;
    @Column(name = "nhbrc_number") String nhbrcNumber;

    // ── Team ──────────────────────────────────────────────────────────────────
    @Column(name = "project_manager_id")   UUID   projectManagerId;
    @Column(name = "project_manager_name") String projectManagerName;

    // ── Portal ────────────────────────────────────────────────────────────────
    @Column(name = "client_portal_token") String clientPortalToken;
    @Column(name = "portal_enabled", nullable = false) boolean portalEnabled = true;
    @Column(name = "portal_token_expires_at") Instant portalTokenExpiresAt;   // null = no expiry (legacy)

    /** 0 = baseline not locked yet; 1 = as first locked; +1 for each approved change order applied. */
    @Column(name = "baseline_version", nullable = false) int baselineVersion = 0;

    // ── Audit / closure ───────────────────────────────────────────────────────
    String notes;

    @Column(name = "cancellation_reason")
    String cancellationReason;

    @Column(name = "closure_notes")   String closureNotes;
    @Column(name = "lessons_learned") String lessonsLearned;

    @Column(name = "created_by") UUID    createdBy;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "cancelled_at") Instant cancelledAt;
    @Column(name = "closed_at")    Instant closedAt;
    @Column(name = "closed_by")    UUID    closedBy;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static Project create(UUID tenantId, String projectNumber, String name,
                                 String projectType, UUID clientId, String clientName,
                                 LocalDate startDate, LocalDate endDate,
                                 BigDecimal budgetTotal, UUID createdBy) {
        Project p         = new Project();
        p.id              = UUID.randomUUID();
        p.tenantId        = tenantId;
        p.projectNumber   = projectNumber;
        p.name            = name;
        p.projectType     = projectType != null ? projectType : "GENERAL";
        p.clientId        = clientId;
        p.clientName      = clientName;
        p.startDate       = startDate;
        p.endDate         = endDate;
        p.baselineStart   = startDate;
        p.baselineEnd     = endDate;
        p.budgetTotal     = budgetTotal != null ? budgetTotal : BigDecimal.ZERO;
        p.status          = ProjectStatus.PLANNING;
        p.health          = ProjectHealth.GREEN;
        p.clientPortalToken = UUID.randomUUID().toString().replace("-", "");
        p.createdBy       = createdBy;
        p.createdAt       = Instant.now();
        p.updatedAt       = Instant.now();
        return p;
    }

    // ── Lifecycle transitions ─────────────────────────────────────────────────

    /** PLANNING -> PROPOSED. Requires a valid schedule so there is something to baseline. */
    public void propose() {
        requireCanTransitionTo(ProjectStatus.PROPOSED);
        if (startDate == null || endDate == null) {
            throw new IllegalStateException(
                    "Start and end dates are required before a project can be proposed for approval");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalStateException("End date cannot be before start date");
        }
        this.status     = ProjectStatus.PROPOSED;
        this.proposedAt = Instant.now();
        touch();
    }

    /** PROPOSED -> APPROVED. Locks the schedule baseline. */
    public void approve(UUID approverId, String approverName) {
        requireCanTransitionTo(ProjectStatus.APPROVED);
        lockBaseline();
        this.status         = ProjectStatus.APPROVED;
        this.approvedAt     = Instant.now();
        this.approvedBy     = approverId;
        this.approvedByName = approverName;
        touch();
    }

    /** PROPOSED -> PLANNING (approver sent it back). */
    public void returnToPlanning() {
        requireCanTransitionTo(ProjectStatus.PLANNING);
        this.status     = ProjectStatus.PLANNING;
        this.proposedAt = null;
        touch();
    }

    public void activate() {
        requireCanTransitionTo(ProjectStatus.ACTIVE);
        // Fast track (no approval step): lock the baseline on first activation.
        if (this.status == ProjectStatus.PLANNING) lockBaseline();
        this.status = ProjectStatus.ACTIVE;
        touch();
    }

    public void hold() {
        requireCanTransitionTo(ProjectStatus.ON_HOLD);
        this.status = ProjectStatus.ON_HOLD;
        touch();
    }

    public void complete() {
        requireCanTransitionTo(ProjectStatus.COMPLETED);
        this.status      = ProjectStatus.COMPLETED;
        this.completedAt = Instant.now();
        touch();
    }

    /** COMPLETED -> ACTIVE. Only valid from COMPLETED (ON_HOLD -> ACTIVE is activate()). */
    public void reopen() {
        if (this.status != ProjectStatus.COMPLETED) {
            throw new IllegalStateException("Only a COMPLETED project can be reopened (current: " + status + ")");
        }
        requireCanTransitionTo(ProjectStatus.ACTIVE);
        this.status      = ProjectStatus.ACTIVE;
        this.completedAt = null;
        touch();
    }

    /** COMPLETED -> CLOSED (terminal). Closure readiness is checked by the lifecycle service. */
    public void close(UUID closedBy, String closureNotes, String lessonsLearned) {
        requireCanTransitionTo(ProjectStatus.CLOSED);
        this.status         = ProjectStatus.CLOSED;
        this.closedAt       = Instant.now();
        this.closedBy       = closedBy;
        this.closureNotes   = closureNotes;
        this.lessonsLearned = lessonsLearned;
        touch();
    }

    /** reason is stored in cancellationReason — never appended to user notes. */
    public void cancel(String reason) {
        requireCanTransitionTo(ProjectStatus.CANCELLED);
        this.status              = ProjectStatus.CANCELLED;
        this.cancellationReason  = reason;
        this.cancelledAt         = Instant.now();
        touch();
    }

    // ── Health calculation ────────────────────────────────────────────────────

    public void updateHealth() {
        BigDecimal totalSpend = budgetSpent.add(budgetCommitted);
        boolean hasBudget     = budgetTotal.compareTo(BigDecimal.ZERO) > 0;

        boolean overBudgetRed   = hasBudget && totalSpend.compareTo(budgetTotal.multiply(BUDGET_RED_FACTOR))   > 0;
        boolean overBudgetAmber = hasBudget && totalSpend.compareTo(budgetTotal.multiply(BUDGET_AMBER_FACTOR)) > 0;

        boolean lateRed   = endDate != null && baselineEnd != null
                && endDate.isAfter(baselineEnd.plusDays(SCHEDULE_RED_DAYS));
        boolean lateAmber = endDate != null && baselineEnd != null
                && endDate.isAfter(baselineEnd.plusDays(SCHEDULE_AMBER_DAYS));

        if (overBudgetRed || lateRed)          this.health = ProjectHealth.RED;
        else if (overBudgetAmber || lateAmber) this.health = ProjectHealth.AMBER;
        else                                   this.health = ProjectHealth.GREEN;

        touch();
    }

    // ── Setters with touch() ──────────────────────────────────────────────────

    public void setBudgetTotal(BigDecimal v)     { this.budgetTotal     = v; touch(); }
    public void setBudgetSpent(BigDecimal v)     { this.budgetSpent     = v; touch(); }
    public void setBudgetCommitted(BigDecimal v) { this.budgetCommitted = v; touch(); }
    public void setProjectManagerId(UUID v)      { this.projectManagerId   = v; touch(); }
    public void setProjectManagerName(String v)  { this.projectManagerName = v; touch(); }
    public void setStartDate(LocalDate v)        { this.startDate     = v; touch(); }
    public void setEndDate(LocalDate v)          { this.endDate       = v; touch(); }
    public void setName(String v)                { this.name          = v; touch(); }
    public void setDescription(String v)         { this.description   = v; touch(); }
    public void setNotes(String v)               { this.notes         = v; touch(); }
    public void setCidbGrade(String v)           { this.cidbGrade     = v; touch(); }
    public void setNhbrcNumber(String v)         { this.nhbrcNumber   = v; touch(); }
    public void setContractValue(BigDecimal v)   { this.contractValue = v; touch(); }
    public void setContractRef(String v)         { this.contractRef   = v; touch(); }
    public void setContractType(String v)        { this.contractType  = v; touch(); }
    public void setRetentionPct(BigDecimal v)    { this.retentionPct  = v != null ? v : BigDecimal.ZERO; touch(); }
    public void setSiteAddress(String v)         { this.siteAddress   = v; touch(); }
    public void setSponsorId(UUID v)             { this.sponsorId     = v; touch(); }
    public void setSponsorName(String v)         { this.sponsorName   = v; touch(); }
    public void setObjectives(String v)          { this.objectives    = v; touch(); }
    public void setScopeStatement(String v)      { this.scopeStatement = v; touch(); }
    public void setBusinessCase(String v)        { this.businessCase  = v; touch(); }

    // ── Client portal access ──────────────────────────────────────────────────

    /** Replaces the token (the old link stops working immediately) and re-enables access. */
    public void rotatePortalToken(String newToken, Instant expiresAt) {
        this.clientPortalToken     = newToken;
        this.portalTokenExpiresAt  = expiresAt;
        this.portalEnabled         = true;
        touch();
    }

    public void disablePortal() { this.portalEnabled = false; touch(); }
    public void enablePortal()  { this.portalEnabled = true;  touch(); }

    public void setPortalTokenExpiresAt(Instant v) { this.portalTokenExpiresAt = v; touch(); }

    /** Single source of truth for "does this project's portal token currently work?" */
    public boolean isPortalAccessible(Instant now) {
        return portalEnabled && (portalTokenExpiresAt == null || now.isBefore(portalTokenExpiresAt));
    }

    // ── Approved change (CHUNK 5) ─────────────────────────────────────────────

    /** Applies an approved change order's schedule effect: BOTH the end date and the approved baseline end. */
    public void applyApprovedSchedule(LocalDate newEnd, LocalDate newBaselineEnd) {
        this.endDate = newEnd;
        this.baselineEnd = newBaselineEnd;
        touch();
    }

    public void setBaselineVersion(int v) { this.baselineVersion = v; touch(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void lockBaseline() {
        this.baselineStart = this.startDate;
        this.baselineEnd   = this.endDate;
        if (this.baselineVersion == 0) this.baselineVersion = 1;
    }

    private void requireCanTransitionTo(ProjectStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot transition project from " + status + " to " + target);
        }
    }

    private void touch() { this.updatedAt = Instant.now(); }
}