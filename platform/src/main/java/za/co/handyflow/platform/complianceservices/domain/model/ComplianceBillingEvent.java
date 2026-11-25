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

/** One scheduled bill from an agreement. Raising it makes a draft invoice in Invoicing; nothing is issued from here. */
@Entity
@Table(name = "compliance_billing_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceBillingEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "agreement_id", nullable = false)
    private UUID agreementId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String status = BillingRules.SCHEDULED;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "raised_by")
    private UUID raisedBy;

    @Column(name = "raised_at")
    private Instant raisedAt;

    @Column(name = "skip_reason", columnDefinition = "TEXT")
    private String skipReason;

    @Column(name = "release_note", columnDefinition = "TEXT")
    private String releaseNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.Version
    private Long version;

    public static ComplianceBillingEvent schedule(TenantId tenantId, ComplianceAgreement a, LocalDate dueDate) {
        ComplianceBillingEvent e = new ComplianceBillingEvent();
        e.tenantId = tenantId;
        e.agreementId = a.getId();
        e.clientId = a.getClientId();
        e.dueDate = dueDate;
        e.amount = a.getBillingAmount();
        e.description = BillingRules.describe(a.getAgreementNumber(), a.getTitle(), dueDate);
        e.createdAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    private void touch() { this.updatedAt = Instant.now(); }

    /** Called once the draft invoice exists. */
    public void invoiced(UUID by, UUID invoiceId, String invoiceNumber) {
        if (!BillingRules.SCHEDULED.equals(status)) throw new IllegalStateException("Only a scheduled bill can be raised");
        if (invoiceId == null) throw new IllegalArgumentException("An invoice is required");
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.raisedBy = by;
        this.raisedAt = Instant.now();
        this.status = BillingRules.INVOICED;
        touch();
    }

    public void skip(String reason) {
        if (!BillingRules.SCHEDULED.equals(status)) throw new IllegalStateException("Only a scheduled bill can be skipped");
        this.skipReason = QuoteRules.note(reason, "The reason", 2000);
        this.status = BillingRules.SKIPPED;
        touch();
    }

    public void restore() {
        if (!BillingRules.SKIPPED.equals(status)) throw new IllegalStateException("Only a skipped bill can be brought back");
        this.skipReason = null;
        this.status = BillingRules.SCHEDULED;
        touch();
    }

    /** Lets go of an invoice that was cancelled or deleted in Invoicing, so the bill can be raised again. The note stays on record. */
    public void release(String invoiceStatus, String note) {
        String p = BillingRules.releaseProblem(status, invoiceStatus);
        if (p != null) throw new IllegalStateException(p);
        this.releaseNote = QuoteRules.note(note, "The reason", 2000) + (invoiceNumber == null ? "" : " (was " + invoiceNumber + ")");
        this.invoiceId = null;
        this.invoiceNumber = null;
        this.raisedBy = null;
        this.raisedAt = null;
        this.status = BillingRules.SCHEDULED;
        touch();
    }
}
