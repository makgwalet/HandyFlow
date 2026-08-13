package za.co.handyflow.platform.recruitmentagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A candidate submitted against a specific requisition — the agency's
 * pipeline unit. One candidate can have multiple RecAgencyPlacement rows
 * over time (different requisitions, possibly across different
 * clients); one requisition can have multiple candidates submitted
 * against it simultaneously.
 * <p>
 * STAGE MACHINE: SUBMITTED -> CLIENT_REVIEW -> CLIENT_INTERVIEW ->
 * OFFERED -> PLACED -> GUARANTEE_PERIOD -> COMPLETED, with terminal
 * exits at several points (REJECTED_BY_CLIENT, WITHDRAWN,
 * CANDIDATE_DECLINED, FAILED_GUARANTEE). This is a genuinely different
 * shape from a normal internal hiring pipeline (recruiter's own
 * SCREENING -> INTERVIEW -> OFFER) — the CLIENT_REVIEW/CLIENT_INTERVIEW
 * split reflects that the agency doesn't control the client's own
 * interview process, only submits and waits; GUARANTEE_PERIOD reflects
 * the placement-fee-refund/replacement window that's standard in
 * recruitment agency contracts (see RecAgencyClient.guaranteePeriodDays).
 * <p>
 * SCOPE NOTE, FLAGGED DELIBERATELY: this entity models the guarantee
 * period as data (guaranteeEndsAt) and a terminal FAILED_GUARANTEE
 * status, but the actual WORKFLOW for what happens when a placement
 * fails within the guarantee window (free replacement search, partial
 * refund, invoice credit note) is NOT built in this pass — real,
 * separate follow-up work once the billing layer exists to act on it.
 */
@Entity
@Table(name = "reca_placements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecAgencyPlacement {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "requisition_id", nullable = false)
    private UUID requisitionId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId; // denormalized from the requisition, for cheaper client-scoped queries

    @Column(name = "stage", nullable = false)
    private String stage = "SUBMITTED";

    @Column(name = "offered_salary", precision = 15, scale = 2)
    private BigDecimal offeredSalary; // set when stage moves to OFFERED/PLACED

    @Column(name = "placement_fee_amount", precision = 15, scale = 2)
    private BigDecimal placementFeeAmount; // computed at PLACED time — offeredSalary x effective fee pct

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "guarantee_ends_at")
    private LocalDate guaranteeEndsAt; // computed at PLACED time from RecAgencyClient.guaranteePeriodDays

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private static final java.util.Set<String> TERMINAL_STAGES = java.util.Set.of(
            "REJECTED_BY_CLIENT", "WITHDRAWN", "CANDIDATE_DECLINED", "FAILED_GUARANTEE", "COMPLETED");

    public static RecAgencyPlacement create(UUID tenantId, UUID requisitionId, UUID candidateId, UUID clientId) {
        RecAgencyPlacement p = new RecAgencyPlacement();
        p.tenantId = tenantId;
        p.requisitionId = requisitionId;
        p.candidateId = candidateId;
        p.clientId = clientId;
        p.stage = "SUBMITTED";
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public boolean isTerminal() {
        return TERMINAL_STAGES.contains(stage);
    }

    /** Advances to a new stage — no illegal-transition enforcement in this
     * foundation pass (unlike Payroll Bureau's PayRun/RecAgencyRequisition
     * status machines) since real recruitment pipelines routinely move
     * backward (client asks to re-interview, candidate re-enters after
     * declining once) — a rigid forward-only state machine would fight
     * real usage here more than it would protect against genuine errors. */
    public void moveToStage(String newStage, String notes) {
        if (isTerminal()) {
            throw new IllegalStateException("Cannot change stage of a " + stage + " placement");
        }
        this.stage = newStage;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void markPlaced(BigDecimal offeredSalary, BigDecimal effectiveFeePct, Integer guaranteePeriodDays) {
        this.stage = "PLACED";
        this.offeredSalary = offeredSalary;
        this.placementFeeAmount = offeredSalary
                .multiply(effectiveFeePct)
                .divide(java.math.BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        this.placedAt = Instant.now();
        if (guaranteePeriodDays != null && guaranteePeriodDays > 0) {
            this.guaranteeEndsAt = LocalDate.now().plusDays(guaranteePeriodDays);
        }
        this.updatedAt = Instant.now();
    }

    /** True once PLACED and either no guarantee period was configured, or it's elapsed. */
    public boolean guaranteePeriodElapsed() {
        return "PLACED".equals(stage) && (guaranteeEndsAt == null || !LocalDate.now().isBefore(guaranteeEndsAt));
    }

    public void completeGuaranteePeriod() {
        if (!"PLACED".equals(stage)) {
            throw new IllegalStateException("Only a PLACED placement can complete its guarantee period");
        }
        this.stage = "COMPLETED";
        this.updatedAt = Instant.now();
    }

    public void failGuarantee(String notes) {
        if (!"PLACED".equals(stage)) {
            throw new IllegalStateException("Only a PLACED placement can fail its guarantee period");
        }
        this.stage = "FAILED_GUARANTEE";
        this.notes = notes;
        this.updatedAt = Instant.now();
    }
}