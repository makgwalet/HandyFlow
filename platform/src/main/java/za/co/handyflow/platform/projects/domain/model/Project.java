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
 * DESIGN DECISIONS
 * ──────────────────
 * 1. Status and health are stored as @Enumerated(EnumType.STRING) — the DB
 *    column stays VARCHAR, but Java is now type-safe.  No more "CANCELD" typos.
 *
 * 2. Health thresholds are named constants, not inline BigDecimal literals.
 *    Previously every call to updateHealth() allocated new BigDecimal("1.10")
 *    objects.  Constants are created once at class-load time.
 *
 * 3. cancel() no longer appends to the user-facing notes field.
 *    The cancellation_reason field (added in V90 migration) is the canonical
 *    place for a system-generated reason.  User notes remain untouched.
 *
 * 4. All mutating methods call touch() to keep updated_at accurate.
 *    Lombok @Setter is NOT used at the class level because setters need to
 *    either call touch() or enforce invariants — a plain Lombok setter does
 *    neither.  Explicit setters make the intent visible.
 */
@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor
public class Project {

    // ── Health threshold constants ────────────────────────────────────────────
    /** Budget-spent+committed > budget × 1.10 → RED */
    private static final BigDecimal BUDGET_RED_FACTOR   = new BigDecimal("1.10");
    /** Budget-spent+committed > budget × 1.05 → AMBER */
    private static final BigDecimal BUDGET_AMBER_FACTOR = new BigDecimal("1.05");
    /** End date slipped > 14 days past baseline → RED */
    private static final int        SCHEDULE_RED_DAYS   = 14;
    /** End date slipped > 7 days past baseline → AMBER */
    private static final int        SCHEDULE_AMBER_DAYS = 7;

    // ── Identity ──────────────────────────────────────────────────────────────
    @Id
    UUID id;

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

    /**
     * Stored as VARCHAR in the DB (check constraint matches enum names).
     * EnumType.STRING means the enum's name() value ("ACTIVE", not ordinal 1)
     * is written to the column — readable without decoding, and safe if enum
     * members are ever reordered.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ProjectStatus status = ProjectStatus.PLANNING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ProjectHealth health = ProjectHealth.GREEN;

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

    // ── Audit ─────────────────────────────────────────────────────────────────
    String notes;

    /**
     * Stores the reason for cancellation separately from user-facing notes.
     * Previously, cancel(reason) appended to the notes field which:
     *   (a) destroyed any existing user note content
     *   (b) mixed system-generated text with user input
     *   (c) made the reason un-queryable
     *
     * V90 migration adds this column (see V90__project_cancellation_reason.sql).
     */
    @Column(name = "cancellation_reason")
    String cancellationReason;

    @Column(name = "created_by") UUID    createdBy;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "cancelled_at") Instant cancelledAt;

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

    public void activate() {
        requireCanTransitionTo(ProjectStatus.ACTIVE);
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

    /**
     * Cancels the project.
     *
     * NOTE: reason is stored in cancellationReason — NOT appended to notes.
     * The original code did: notes = notes + "\nCancelled: " + reason
     * which corrupted the user's own notes field.
     */
    public void cancel(String reason) {
        requireCanTransitionTo(ProjectStatus.CANCELLED);
        this.status              = ProjectStatus.CANCELLED;
        this.cancellationReason  = reason;
        this.cancelledAt         = Instant.now();
        touch();
    }

    // ── Health calculation ────────────────────────────────────────────────────

    /**
     * Recalculates RAG health based on budget consumption and schedule variance.
     *
     * Thresholds (named constants at top of class — not magic numbers):
     *   RED:   spent+committed > budget × 1.10  OR  endDate > baselineEnd + 14 days
     *   AMBER: spent+committed > budget × 1.05  OR  endDate > baselineEnd + 7 days
     *   GREEN: within all thresholds
     */
    public void updateHealth() {
        BigDecimal totalSpend = budgetSpent.add(budgetCommitted);
        boolean hasBudget     = budgetTotal.compareTo(BigDecimal.ZERO) > 0;

        boolean overBudgetRed   = hasBudget && totalSpend.compareTo(budgetTotal.multiply(BUDGET_RED_FACTOR))   > 0;
        boolean overBudgetAmber = hasBudget && totalSpend.compareTo(budgetTotal.multiply(BUDGET_AMBER_FACTOR)) > 0;

        boolean lateRed   = endDate != null && baselineEnd != null
                && endDate.isAfter(baselineEnd.plusDays(SCHEDULE_RED_DAYS));
        boolean lateAmber = endDate != null && baselineEnd != null
                && endDate.isAfter(baselineEnd.plusDays(SCHEDULE_AMBER_DAYS));

        if (overBudgetRed || lateRed)     this.health = ProjectHealth.RED;
        else if (overBudgetAmber || lateAmber) this.health = ProjectHealth.AMBER;
        else                               this.health = ProjectHealth.GREEN;

        touch();
    }

    // ── Setters with touch() ──────────────────────────────────────────────────
    // Each setter calls touch() so updated_at is always accurate.
    // Lombok @Setter is NOT used at class level because it generates plain setters
    // that don't call touch() — silent audit-trail gaps.

    public void setBudgetTotal(BigDecimal v)     { this.budgetTotal     = v; touch(); }
    public void setBudgetSpent(BigDecimal v)     { this.budgetSpent     = v; touch(); }
    public void setBudgetCommitted(BigDecimal v) { this.budgetCommitted = v; touch(); }
    public void setProjectManagerId(UUID v)      { this.projectManagerId   = v; touch(); }
    public void setProjectManagerName(String v)  { this.projectManagerName = v; touch(); }
    public void setEndDate(LocalDate v)          { this.endDate       = v; touch(); }
    public void setName(String v)                { this.name          = v; touch(); }
    public void setDescription(String v)         { this.description   = v; touch(); }
    public void setNotes(String v)               { this.notes         = v; touch(); }
    public void setCidbGrade(String v)           { this.cidbGrade     = v; touch(); }
    public void setNhbrcNumber(String v)         { this.nhbrcNumber   = v; touch(); }
    public void setContractValue(BigDecimal v)   { this.contractValue = v; touch(); }
    public void setContractRef(String v)         { this.contractRef   = v; touch(); }
    public void setSiteAddress(String v)         { this.siteAddress   = v; touch(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requireCanTransitionTo(ProjectStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot transition project from " + status + " to " + target);
        }
    }

    private void touch() { this.updatedAt = Instant.now(); }
}
