package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 2 — the core tender record. Deliberately does NOT yet reference
 * Projects/HR/Fleet/Accounting for project experience, personnel,
 * equipment, or financial data — the source material's own explicit
 * design principle is "don't copy the data, reference it," which needs
 * those modules' own facades to build against properly rather than being
 * bolted on as an afterthought. This is the tender record itself and its
 * lifecycle; the reference wiring is a deliberate next step, added when
 * there's real code that needs it (same discipline this whole module has
 * followed since Phase 1 — see the strategic roadmap backlog, Part 6).
 * <p>
 * Status lifecycle, following the source material's own two-stage shape
 * (an internal preparation/approval flow, then an external outcome
 * flow), collapsed into one status field rather than two separate ones —
 * simpler for Phase 2, revisit if a tender genuinely needs to be "in
 * commercial review" and "shortlisted" to be independently true at once,
 * which the source material's own examples never actually show:
 * <pre>
 * DRAFT → IN_PREPARATION → INTERNAL_REVIEW → READY_TO_SUBMIT → SUBMITTED
 *       → CLARIFICATION → SHORTLISTED → NEGOTIATION → AWARDED
 *                                                     → UNSUCCESSFUL
 * (WITHDRAWN reachable from any pre-SUBMITTED state)
 * </pre>
 */
@Entity
@Table(name = "tenders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tender {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tender_number", nullable = false)
    private String tenderNumber; // this tenant's own reference, via TenantNumberingFacade — see TenderService

    @Column(nullable = false)
    private String name;

    @Column(name = "tender_authority")
    private String tenderAuthority; // the client/government body issuing the tender

    @Column(name = "authority_reference_number")
    private String authorityReferenceNumber; // THEIR reference number, not ours

    @Column(name = "closing_date")
    private LocalDate closingDate;

    @Column(name = "briefing_date")
    private LocalDate briefingDate;

    @Column(name = "site_inspection_date")
    private LocalDate siteInspectionDate;

    @Column(name = "estimated_value", precision = 15, scale = 2)
    private BigDecimal estimatedValue;

    private String industry;

    @Column(name = "required_class_of_work")
    private String requiredClassOfWork; // free text for Phase 2 — e.g. "cidb Grade 6GB"

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(name = "outcome_reason", columnDefinition = "TEXT")
    private String outcomeReason;

    @Column(name = "awarded_value", precision = 15, scale = 2)
    private BigDecimal awardedValue;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    public static Tender create(TenantId tenantId, String tenderNumber, String name, String tenderAuthority,
                                String authorityReferenceNumber, LocalDate closingDate, LocalDate briefingDate,
                                LocalDate siteInspectionDate, BigDecimal estimatedValue, String industry,
                                String requiredClassOfWork, UUID createdBy) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        Tender t = new Tender();
        t.tenantId = tenantId;
        t.tenderNumber = tenderNumber;
        t.name = name;
        t.tenderAuthority = tenderAuthority;
        t.authorityReferenceNumber = authorityReferenceNumber;
        t.closingDate = closingDate;
        t.briefingDate = briefingDate;
        t.siteInspectionDate = siteInspectionDate;
        t.estimatedValue = estimatedValue;
        t.industry = industry;
        t.requiredClassOfWork = requiredClassOfWork;
        t.createdAt = Instant.now();
        t.createdBy = createdBy;
        t.updatedAt = Instant.now();
        t.updatedBy = createdBy;
        return t;
    }

    private static final java.util.Map<String, java.util.Set<String>> ALLOWED_TRANSITIONS = java.util.Map.ofEntries(
            java.util.Map.entry("DRAFT",             java.util.Set.of("IN_PREPARATION", "WITHDRAWN")),
            java.util.Map.entry("IN_PREPARATION",    java.util.Set.of("INTERNAL_REVIEW", "WITHDRAWN")),
            java.util.Map.entry("INTERNAL_REVIEW",   java.util.Set.of("READY_TO_SUBMIT", "IN_PREPARATION", "WITHDRAWN")),
            java.util.Map.entry("READY_TO_SUBMIT",   java.util.Set.of("SUBMITTED", "WITHDRAWN")),
            java.util.Map.entry("SUBMITTED",         java.util.Set.of("CLARIFICATION", "SHORTLISTED", "UNSUCCESSFUL")),
            java.util.Map.entry("CLARIFICATION",     java.util.Set.of("SHORTLISTED", "UNSUCCESSFUL")),
            java.util.Map.entry("SHORTLISTED",       java.util.Set.of("NEGOTIATION", "AWARDED", "UNSUCCESSFUL")),
            java.util.Map.entry("NEGOTIATION",       java.util.Set.of("AWARDED", "UNSUCCESSFUL"))
    );

    /**
     * Explicit transition table rather than letting any status be set
     * directly — the source material's own tender lifecycle is a real
     * workflow with a meaningful order (you can't be AWARDED without
     * having been SUBMITTED first), and silently allowing an arbitrary
     * jump would make "what actually happened to this tender" an
     * unreliable question to ask later, exactly the audit concern the
     * source material raises for tender submission snapshots.
     */
    public void transitionTo(String newStatus, UUID updatedBy) {
        java.util.Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(status, java.util.Set.of());
        if (!allowed.contains(newStatus))
            throw new IllegalStateException("Cannot transition tender from " + status + " to " + newStatus);
        this.status = newStatus;
        if ("SUBMITTED".equals(newStatus)) this.submittedAt = Instant.now();
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    public void recordOutcome(String outcome, String reason, BigDecimal awardedValue, UUID updatedBy) {
        transitionTo(outcome, updatedBy); // validates AWARDED/UNSUCCESSFUL is actually reachable from the current status
        this.outcomeReason = reason;
        this.awardedValue = awardedValue;
    }

    public boolean isSubmitted() { return submittedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
