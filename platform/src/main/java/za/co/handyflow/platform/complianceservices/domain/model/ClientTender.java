package za.co.handyflow.platform.complianceservices.domain.model;

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
 * The client-scoped counterpart to compliancetender.Tender — same
 * reasoning as ClientComplianceRegistration's own Javadoc for the
 * parallel-table design, and the identical lifecycle/transition table.
 * Deliberately does NOT yet reference Projects/HR/Fleet/Accounting for
 * project experience, personnel, or financial data — same reasoning
 * compliancetender.Tender's own Javadoc gives: "don't copy the data,
 * reference it" needs those modules' own facades checked before building
 * against them, and here there's the added question of whose data would
 * even be referenced (the SERVICE PROVIDER's own HR records, presumably,
 * for personnel put forward on the client's tender — not built here,
 * flagged for whoever picks this up next).
 */
@Entity
@Table(name = "client_tenders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientTender {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "tender_number", nullable = false)
    private String tenderNumber;

    @Column(nullable = false)
    private String name;

    @Column(name = "tender_authority")
    private String tenderAuthority;

    @Column(name = "authority_reference_number")
    private String authorityReferenceNumber;

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
    private String requiredClassOfWork;

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

    public static ClientTender create(TenantId tenantId, UUID clientId, String tenderNumber, String name,
                                      String tenderAuthority, String authorityReferenceNumber, LocalDate closingDate,
                                      LocalDate briefingDate, LocalDate siteInspectionDate, BigDecimal estimatedValue,
                                      String industry, String requiredClassOfWork, UUID createdBy) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        ClientTender t = new ClientTender();
        t.tenantId = tenantId;
        t.clientId = clientId;
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

    // Identical to Tender.java's own ALLOWED_TRANSITIONS — same lifecycle, deliberately kept in sync.
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
        transitionTo(outcome, updatedBy);
        this.outcomeReason = reason;
        this.awardedValue = awardedValue;
    }

    public boolean isSubmitted() { return submittedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
