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
 * A quote for one client. It is written as a draft and priced by the server on the price book version pinned at the
 * start. A discount above the consultant limit goes to an administrator, who is not the author unless they record why.
 * Sending and the client's answer are recorded by a person; nothing here emails anyone.
 */
@Entity
@Table(name = "compliance_quotes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceQuote {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "quote_number", nullable = false)
    private String quoteNumber;

    private String title;

    @Column(nullable = false)
    private String status = QuoteRules.DRAFT;

    @Column(name = "price_book_version", nullable = false)
    private int priceBookVersion;

    @Column(nullable = false)
    private String currency = "ZAR";

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String terms;

    @Column(name = "required_approver", nullable = false)
    private String requiredApprover = "CONSULTANT";

    @Column(name = "approval_basis", columnDefinition = "TEXT")
    private String approvalBasis;

    @Column(name = "max_discount_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxDiscountPct = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal professional = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal disbursements = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal vat = BigDecimal.ZERO;

    @Column(name = "vat_rate_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRatePct = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_note", columnDefinition = "TEXT")
    private String approvalNote;

    @Column(name = "approval_exception_reason", columnDefinition = "TEXT")
    private String approvalExceptionReason;

    @Column(name = "sent_by")
    private UUID sentBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "withdrawn_reason", columnDefinition = "TEXT")
    private String withdrawnReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.Version
    private Long version;

    public static ComplianceQuote draft(TenantId tenantId, UUID clientId, String number, int priceBookVersion, String currency, UUID by) {
        ComplianceQuote q = new ComplianceQuote();
        q.tenantId = tenantId;
        q.clientId = clientId;
        q.quoteNumber = number;
        q.priceBookVersion = priceBookVersion;
        if (currency != null) q.currency = currency;
        q.createdBy = by;
        q.createdAt = Instant.now();
        q.updatedAt = Instant.now();
        return q;
    }

    public boolean isDraft() { return QuoteRules.DRAFT.equals(status); }

    private void touch() { this.updatedAt = Instant.now(); }

    public void edit(String title, LocalDate validUntil, String notes, String terms, LocalDate today) {
        if (!isDraft()) throw new IllegalStateException("Only a draft can be edited. Withdraw this quote and start another to change it.");
        if (validUntil != null) {
            String p = QuoteRules.validUntilProblem(validUntil, today);
            if (p != null) throw new IllegalArgumentException(p);
        }
        this.title = ClientProfileRules.clean(title, 255);
        this.validUntil = validUntil;
        this.notes = ClientProfileRules.clean(notes, 4000);
        this.terms = ClientProfileRules.clean(terms, 4000);
        touch();
    }

    /** Takes the server's figures. Only a draft is priced. */
    public void applyPricing(QuoteRules.Totals t, String requiredApprover, BigDecimal maxDiscountPct) { applyPricing(t, requiredApprover, maxDiscountPct, null); }

    public void applyPricing(QuoteRules.Totals t, String requiredApprover, BigDecimal maxDiscountPct, String approvalBasis) {
        if (!isDraft()) throw new IllegalStateException("Only a draft can be priced");
        this.professional = t.professional();
        this.discountTotal = t.discount();
        this.disbursements = t.disbursements();
        this.vat = t.vat();
        this.total = t.total();
        this.vatRatePct = t.vatRatePct();
        this.requiredApprover = requiredApprover;
        this.approvalBasis = approvalBasis;
        this.maxDiscountPct = maxDiscountPct;
        touch();
    }

    /** A discount within the consultant limit needs no approval, so the quote is ready to send. */
    public void submit(UUID by, int lines, LocalDate today) {
        if (!isDraft()) throw new IllegalStateException("Only a draft can be submitted");
        if (lines < 1) throw new IllegalArgumentException("Add at least one service before submitting");
        String p = QuoteRules.validUntilProblem(validUntil, today);
        if (p != null) throw new IllegalArgumentException(p);
        this.submittedBy = by;
        this.submittedAt = Instant.now();
        this.approvalNote = null;
        this.status = QuoteRules.needsApproval(requiredApprover) ? QuoteRules.PENDING_APPROVAL : QuoteRules.APPROVED;
        touch();
    }

    /** The reviewer is not the person who wrote or submitted it, unless they record why no one else can. */
    public void approve(UUID reviewer, String note, String exceptionReason) {
        if (!QuoteRules.PENDING_APPROVAL.equals(status)) throw new IllegalStateException("Only a quote waiting for approval can be approved");
        if (reviewer == null) throw new IllegalArgumentException("A reviewer is required");
        String reason = null;
        if (reviewer.equals(createdBy) || reviewer.equals(submittedBy)) {
            String r = exceptionReason == null ? "" : exceptionReason.trim();
            if (r.length() < ObligationRules.MIN_EXCEPTION_REASON) {
                throw new IllegalArgumentException("You wrote or submitted this quote, so someone else should approve the discount. If no one else can, give a reason of at least "
                        + ObligationRules.MIN_EXCEPTION_REASON + " characters");
            }
            reason = r.length() > 1000 ? r.substring(0, 1000) : r;
        }
        this.status = QuoteRules.APPROVED;
        this.approvedBy = reviewer;
        this.approvedAt = Instant.now();
        this.approvalNote = ClientProfileRules.clean(note, 2000);
        this.approvalExceptionReason = reason;
        touch();
    }

    public void reject(UUID reviewer, String reason) {
        if (!QuoteRules.PENDING_APPROVAL.equals(status)) throw new IllegalStateException("Only a quote waiting for approval can be sent back");
        this.approvalNote = QuoteRules.note(reason, "The reason", 2000);
        this.approvedBy = null;
        this.approvedAt = null;
        this.status = QuoteRules.DRAFT;
        touch();
    }

    public void markSent(UUID by, LocalDate today) {
        if (!QuoteRules.APPROVED.equals(status)) throw new IllegalStateException("Only an approved quote can be marked as sent");
        if (validUntil == null || validUntil.isBefore(today)) throw new IllegalArgumentException("This quote has passed its date. Withdraw it and start another");
        this.status = QuoteRules.SENT;
        this.sentBy = by;
        this.sentAt = Instant.now();
        touch();
    }

    /** The note says how the client accepted: who, and by what means. A document on file is the person's to attach. */
    public void accept(UUID by, String note, LocalDate today) {
        requireOpenSent(today);
        String n = QuoteRules.note(note, "How the client accepted", 2000);
        this.status = QuoteRules.ACCEPTED;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decisionNote = n;
        touch();
    }

    public void decline(UUID by, String note, LocalDate today) {
        if (!QuoteRules.SENT.equals(status)) throw new IllegalStateException("Only a sent quote can be declined");
        String n = QuoteRules.note(note, "The client's reason", 2000);
        this.status = QuoteRules.DECLINED;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decisionNote = n;
        touch();
    }

    public void withdraw(String reason) {
        if (!QuoteRules.CAN_WITHDRAW.contains(status)) throw new IllegalStateException("A quote that has been answered cannot be withdrawn");
        this.withdrawnReason = QuoteRules.note(reason, "The reason", 2000);
        this.status = QuoteRules.WITHDRAWN;
        touch();
    }

    private void requireOpenSent(LocalDate today) {
        if (!QuoteRules.SENT.equals(status)) throw new IllegalStateException("Only a sent quote can be accepted");
        if (validUntil != null && validUntil.isBefore(today)) throw new IllegalStateException("This quote expired on " + validUntil + ". Start a new one rather than accepting it late");
    }

    public String effectiveStatus(LocalDate today) { return QuoteRules.effectiveStatus(status, validUntil, today); }
}
