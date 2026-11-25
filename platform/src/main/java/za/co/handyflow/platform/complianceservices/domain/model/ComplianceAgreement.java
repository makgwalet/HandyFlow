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
 * The engagement that follows an accepted quote: the scope as quoted, a term and how the client is billed. A person
 * activates it and records how the client confirmed. Nothing here invoices anyone; the schedule is a preview.
 */
@Entity
@Table(name = "compliance_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceAgreement {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Column(name = "agreement_number", nullable = false)
    private String agreementNumber;

    private String title;

    @Column(nullable = false)
    private String status = AgreementRules.DRAFT;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "billing_frequency", nullable = false)
    private String billingFrequency = AgreementRules.ONCE;

    @Column(name = "billing_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal billingAmount = BigDecimal.ZERO;

    @Column(name = "first_billing_date", nullable = false)
    private LocalDate firstBillingDate;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "notice_days", nullable = false)
    private int noticeDays = 30;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String terms;

    @Column(name = "quoted_professional", nullable = false, precision = 14, scale = 2)
    private BigDecimal quotedProfessional = BigDecimal.ZERO;

    @Column(name = "quoted_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal quotedTotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "ZAR";

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "activated_by")
    private UUID activatedBy;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "activation_note", columnDefinition = "TEXT")
    private String activationNote;

    @Column(name = "suspended_reason", columnDefinition = "TEXT")
    private String suspendedReason;

    @Column(name = "ended_by")
    private UUID endedBy;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "end_reason", columnDefinition = "TEXT")
    private String endReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.Version
    private Long version;

    /** What a person fills in. */
    public record Terms(String title, LocalDate startDate, LocalDate endDate, String billingFrequency, BigDecimal billingAmount,
                        LocalDate firstBillingDate, boolean autoRenew, int noticeDays, String notes, String terms) {}

    public static ComplianceAgreement draft(TenantId tenantId, UUID clientId, UUID quoteId, String number, BigDecimal quotedProfessional,
                                            BigDecimal quotedTotal, String currency, Terms t, UUID by) {
        ComplianceAgreement a = new ComplianceAgreement();
        a.tenantId = tenantId;
        a.clientId = clientId;
        a.quoteId = quoteId;
        a.agreementNumber = number;
        a.quotedProfessional = quotedProfessional;
        a.quotedTotal = quotedTotal;
        if (currency != null) a.currency = currency;
        a.createdBy = by;
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        a.apply(t);
        return a;
    }

    public boolean isDraft() { return AgreementRules.DRAFT.equals(status); }

    private void touch() { this.updatedAt = Instant.now(); }

    public void edit(Terms t) {
        if (!isDraft()) throw new IllegalStateException("Only a draft can be edited. End this agreement and start another to change the terms");
        apply(t);
        touch();
    }

    private void apply(Terms t) {
        String freq = t.billingFrequency() == null ? null : t.billingFrequency().trim().toUpperCase();
        String p = AgreementRules.termProblem(t.startDate(), t.endDate());
        if (p == null) p = AgreementRules.billingProblem(freq, t.billingAmount(), t.firstBillingDate(), t.startDate(), t.endDate());
        if (p == null) p = AgreementRules.noticeProblem(t.noticeDays());
        if (p != null) throw new IllegalArgumentException(p);
        this.title = ClientProfileRules.clean(t.title(), 255);
        this.startDate = t.startDate();
        this.endDate = t.endDate();
        this.billingFrequency = freq;
        this.billingAmount = t.billingAmount().setScale(2, java.math.RoundingMode.HALF_UP);
        this.firstBillingDate = t.firstBillingDate();
        this.autoRenew = t.autoRenew();
        this.noticeDays = t.noticeDays();
        this.notes = ClientProfileRules.clean(t.notes(), 4000);
        this.terms = ClientProfileRules.clean(t.terms(), 4000);
    }

    /** The note says how the client confirmed: who, and by what means. A signed copy is the person's to attach. */
    public void activate(UUID by, String note) {
        if (!isDraft()) throw new IllegalStateException("Only a draft can be activated");
        this.activationNote = QuoteRules.note(note, "How the client confirmed", 2000);
        this.activatedBy = by;
        this.activatedAt = Instant.now();
        this.status = AgreementRules.ACTIVE;
        touch();
    }

    public void suspend(String reason) {
        if (!AgreementRules.ACTIVE.equals(status)) throw new IllegalStateException("Only an active agreement can be suspended");
        this.suspendedReason = QuoteRules.note(reason, "The reason", 2000);
        this.status = AgreementRules.SUSPENDED;
        touch();
    }

    public void resume() {
        if (!AgreementRules.SUSPENDED.equals(status)) throw new IllegalStateException("Only a suspended agreement can be resumed");
        this.suspendedReason = null;
        this.status = AgreementRules.ACTIVE;
        touch();
    }

    /** Ending keeps everything on record. The end date becomes the date it actually stopped. */
    public void end(UUID by, LocalDate date, String reason) {
        if (!AgreementRules.ACTIVE.equals(status) && !AgreementRules.SUSPENDED.equals(status)) {
            throw new IllegalStateException("Only an active or suspended agreement can be ended");
        }
        if (date == null) throw new IllegalArgumentException("Say the date it ends");
        if (date.isBefore(startDate)) throw new IllegalArgumentException("The end date cannot be before the start date");
        this.endReason = QuoteRules.note(reason, "The reason", 2000);
        this.endDate = date;
        this.endedBy = by;
        this.endedAt = Instant.now();
        this.status = AgreementRules.ENDED;
        touch();
    }

    /** A draft that was never activated. It stays on record, and the quote can be used for another agreement. */
    public void cancel(String reason) {
        if (!isDraft()) throw new IllegalStateException("Only a draft can be cancelled. End an active agreement instead");
        this.endReason = QuoteRules.note(reason, "The reason", 2000);
        this.status = AgreementRules.CANCELLED;
        touch();
    }
}
