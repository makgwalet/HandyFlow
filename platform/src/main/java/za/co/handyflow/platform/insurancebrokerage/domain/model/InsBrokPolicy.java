package za.co.handyflow.platform.insurancebrokerage.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per policy TERM, client-scoped — same one-row-per-cycle shape
 * {@code InsPolicy} (internal) already uses via {@code renewalOfPolicyId}
 * (a renewal creates a new row, chained, never mutates the old one).
 * <p>
 * LIFECYCLE — the difference from {@code InsPolicy} (internal), which
 * starts directly ACTIVE because it records cover a tenant already
 * holds: this module models an actual quote/bind workflow, because a
 * brokerage genuinely originates new business for a client rather than
 * just recording existing cover.
 * <pre>
 *   New business:  QUOTE -&gt; bind() -&gt; BOUND -&gt; activate() -&gt; ACTIVE
 *   Renewal:       renew() on an ACTIVE/LAPSED row creates a NEW row
 *                  directly in ACTIVE (no re-quoting — this is a
 *                  continuation of existing cover, same choice
 *                  InsPolicyService.renew() already made).
 *   From ACTIVE:   markLapsed() -&gt; LAPSED -&gt; reinstate() -&gt; ACTIVE
 *   Terminal:      cancel() from QUOTE/BOUND/ACTIVE/LAPSED -&gt; CANCELLED
 *                  (with reason); system-driven EXPIRED once expiryDate
 *                  has passed with no renewal (see
 *                  InsBrokNotificationScheduler); RENEWED is set on the
 *                  OLD row the instant renew() creates the new term.
 * </pre>
 * {@code activate()} is the SINGLE hook where a commission invoice is
 * generated (via {@code InsBrokCommissionInvoiceService.issueForPolicy()},
 * called from {@code InsBrokPolicyService}, never from this entity
 * itself — entities in this codebase don't reach across to other
 * services) — whether ACTIVE was reached via the QUOTE/BOUND path or
 * created there directly by {@code renew()}, so commission-generation
 * logic exists in exactly one place.
 * <p>
 * {@code commissionRatePct} is nullable — when unset, the client's own
 * {@code defaultCommissionRatePct} is used at commission-issue time
 * (see {@code InsBrokCommissionInvoiceService.resolveCommissionRate()});
 * if NEITHER is set, commission issue fails loudly rather than guessing
 * a rate — same revenue-critical guard
 * {@code CollAgencyTrustTransactionService.resolveCommissionRate()}
 * already enforces.
 */
@Entity
@Table(name = "insbrok_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsBrokPolicy {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "insurer_id", nullable = false)
    private UUID insurerId;

    @Column(name = "policy_number")
    private String policyNumber; // set at bind() — a QUOTE has none yet

    @Column(name = "quote_reference")
    private String quoteReference;

    @Column(name = "line_of_business", nullable = false)
    private String lineOfBusiness; // MOTOR | PROPERTY | HOME | LIABILITY | COMMERCIAL_ASSET | OTHER

    @Column(name = "asset_type")
    private String assetType; // freeform — see InsPolicy's own §4 rationale; same choice here

    @Column(name = "asset_reference")
    private String assetReference;

    @Column(name = "sum_insured", precision = 15, scale = 2)
    private BigDecimal sumInsured;

    @Column(name = "premium_amount", precision = 15, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "premium_frequency")
    private String premiumFrequency; // MONTHLY | QUARTERLY | ANNUAL

    @Column(name = "excess_amount", precision = 15, scale = 2)
    private BigDecimal excessAmount;

    @Column(name = "commission_rate_pct", precision = 5, scale = 2)
    private BigDecimal commissionRatePct; // nullable override of client default

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "status", nullable = false)
    private String status; // QUOTE | BOUND | ACTIVE | LAPSED | CANCELLED | EXPIRED | RENEWED

    @Column(name = "bound_at")
    private Instant boundAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "cancelled_date")
    private LocalDate cancelledDate;

    @Column(name = "cancel_reason", length = 1000)
    private String cancelReason;

    @Column(name = "renewal_of_policy_id")
    private UUID renewalOfPolicyId;

    @Column(name = "expiry_reminder_sent_at")
    private Instant expiryReminderSentAt;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static InsBrokPolicy createQuote(UUID tenantId, UUID clientId, UUID insurerId, String quoteReference,
                                             String lineOfBusiness, String assetType, String assetReference,
                                             BigDecimal sumInsured, BigDecimal premiumAmount,
                                             String premiumFrequency, BigDecimal excessAmount,
                                             BigDecimal commissionRatePct, LocalDate startDate, LocalDate expiryDate,
                                             String notes) {
        InsBrokPolicy p = new InsBrokPolicy();
        p.tenantId = tenantId;
        p.clientId = clientId;
        p.insurerId = insurerId;
        p.quoteReference = quoteReference;
        p.lineOfBusiness = lineOfBusiness;
        p.assetType = assetType;
        p.assetReference = assetReference;
        p.sumInsured = sumInsured;
        p.premiumAmount = premiumAmount;
        p.premiumFrequency = premiumFrequency;
        p.excessAmount = excessAmount;
        p.commissionRatePct = commissionRatePct;
        p.startDate = startDate;
        p.expiryDate = expiryDate;
        p.notes = notes;
        p.status = "QUOTE";
        Instant now = Instant.now();
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    /**
     * Used only by {@code InsBrokPolicyService.renew()} — a renewal is a
     * continuation, not a new quote. Public (not package-private) because
     * the service lives in {@code application.internal}, a different
     * Java package from this entity's own {@code domain.model} — same
     * visibility {@code InsBrokPolicy.createQuote()} already has, for
     * the same reason.
     */
    public static InsBrokPolicy createRenewalTerm(InsBrokPolicy previous, String policyNumber, BigDecimal sumInsured,
                                            BigDecimal premiumAmount, LocalDate startDate, LocalDate expiryDate) {
        InsBrokPolicy p = new InsBrokPolicy();
        p.tenantId = previous.tenantId;
        p.clientId = previous.clientId;
        p.insurerId = previous.insurerId;
        p.policyNumber = policyNumber;
        p.lineOfBusiness = previous.lineOfBusiness;
        p.assetType = previous.assetType;
        p.assetReference = previous.assetReference;
        p.sumInsured = sumInsured;
        p.premiumAmount = premiumAmount;
        p.premiumFrequency = previous.premiumFrequency;
        p.excessAmount = previous.excessAmount;
        p.commissionRatePct = previous.commissionRatePct;
        p.startDate = startDate;
        p.expiryDate = expiryDate;
        p.renewalOfPolicyId = previous.id;
        p.status = "ACTIVE";
        Instant now = Instant.now();
        p.activatedAt = now;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public void update(UUID insurerId, String lineOfBusiness, String assetType, String assetReference,
                        BigDecimal sumInsured, BigDecimal premiumAmount, String premiumFrequency,
                        BigDecimal excessAmount, BigDecimal commissionRatePct, LocalDate startDate,
                        LocalDate expiryDate, String notes) {
        assertMutable();
        this.insurerId = insurerId;
        this.lineOfBusiness = lineOfBusiness;
        this.assetType = assetType;
        this.assetReference = assetReference;
        this.sumInsured = sumInsured;
        this.premiumAmount = premiumAmount;
        this.premiumFrequency = premiumFrequency;
        this.excessAmount = excessAmount;
        this.commissionRatePct = commissionRatePct;
        this.startDate = startDate;
        this.expiryDate = expiryDate;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void bind(String policyNumber) {
        if (!"QUOTE".equals(status)) {
            throw new IllegalStateException("Only a QUOTE can be bound (current status: " + status + ")");
        }
        this.policyNumber = policyNumber;
        this.status = "BOUND";
        this.boundAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** The single hook point where commission is generated — see class Javadoc. */
    public void activate() {
        if (!"BOUND".equals(status)) {
            throw new IllegalStateException("Only a BOUND policy can be activated (current status: " + status + ")");
        }
        this.status = "ACTIVE";
        this.activatedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markLapsed() {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("Only an ACTIVE policy can be marked LAPSED (current status: " + status + ")");
        }
        this.status = "LAPSED";
        this.updatedAt = Instant.now();
    }

    public void reinstate() {
        if (!"LAPSED".equals(status)) {
            throw new IllegalStateException("Only a LAPSED policy can be reinstated (current status: " + status + ")");
        }
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if (java.util.List.of("CANCELLED", "EXPIRED", "RENEWED").contains(status)) {
            throw new IllegalStateException("Cannot cancel a policy already in a terminal state (" + status + ")");
        }
        this.status = "CANCELLED";
        this.cancelledDate = LocalDate.now();
        this.cancelReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markExpired() {
        if (!java.util.List.of("ACTIVE", "LAPSED").contains(status)) {
            throw new IllegalStateException("Only ACTIVE/LAPSED can expire (current status: " + status + ")");
        }
        this.status = "EXPIRED";
        this.updatedAt = Instant.now();
    }

    public void markRenewed() {
        if (!java.util.List.of("ACTIVE", "LAPSED").contains(status)) {
            throw new IllegalStateException("Only ACTIVE/LAPSED can be renewed (current status: " + status + ")");
        }
        this.status = "RENEWED";
        this.updatedAt = Instant.now();
    }

    public void markExpiryReminderSent() {
        this.expiryReminderSentAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    private void assertMutable() {
        if (java.util.List.of("CANCELLED", "EXPIRED", "RENEWED").contains(status)) {
            throw new IllegalStateException("Cannot edit a policy in a terminal state (" + status + ")");
        }
    }
}
