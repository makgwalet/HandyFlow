package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A tenant-defined, versioned, effective-dated rule for a fee that may be charged to a debtor. NOTHING IS SEEDED:
 * whether a fee may lawfully be charged — and its cap — depends on the regime that applies (an original creditor
 * collecting its own debt versus a registered debt collector acting for others, the type of agreement, the
 * current regulations). So every rule must carry a legalBasisNote explaining why it is lawful, be approved by a
 * second person, and is then versioned: changing a rule creates a new DRAFT version, and sent charges keep
 * pointing at the exact version applied.
 * <p>
 * Limits supported: a cumulative maxPerCase for the rule, and maxChargesPerMonth per case (e.g. a small fee per
 * necessary electronic communication, capped at N per month). The calculator enforces both.
 * (Field is ruleVersion, not version, to avoid clashing with the aggregate's optimistic-lock field.)
 */
@Entity
@Table(name = "debtcollection_fee_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeRule extends AggregateRoot<FeeRule> {

    @Column(nullable = false, length = 60)
    private String code;

    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;

    @Column(nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "calc_method", nullable = false, length = 25)
    private FeeCalcMethod calcMethod;

    @Column(name = "fixed_amount", precision = 15, scale = 2)
    private BigDecimal fixedAmount;

    /** Percent, e.g. 5.00 = 5%. */
    @Column(name = "percent_of_outstanding", precision = 7, scale = 4)
    private BigDecimal percentOfOutstanding;

    @Column(name = "max_per_case", precision = 15, scale = 2)
    private BigDecimal maxPerCase;

    @Column(name = "max_charges_per_month")
    private Integer maxChargesPerMonth;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "legal_basis_note", nullable = false, columnDefinition = "TEXT")
    private String legalBasisNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private FeeRuleStatus status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_by_name", length = 255)
    private String approvedByName;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "retired_reason", length = 500)
    private String retiredReason;

    public static FeeRule draft(TenantId tenantId, String code, int ruleVersion, String description, FeeCalcMethod method,
                                BigDecimal fixedAmount, BigDecimal percent, BigDecimal maxPerCase,
                                Integer maxChargesPerMonth, LocalDate effectiveFrom, LocalDate effectiveTo,
                                String legalBasisNote, UUID createdBy) {
        if (code == null || !code.matches("[A-Z0-9_]{3,60}")) {
            throw new IllegalArgumentException("code must be 3-60 characters of A-Z, 0-9 or underscore");
        }
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");
        if (method == null) throw new IllegalArgumentException("calcMethod is required");
        if (method == FeeCalcMethod.FIXED && (fixedAmount == null || fixedAmount.signum() <= 0)) {
            throw new IllegalArgumentException("A FIXED fee needs a positive fixedAmount");
        }
        if (method == FeeCalcMethod.PERCENT_OF_OUTSTANDING
                && (percent == null || percent.signum() <= 0 || percent.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("A percentage fee needs a percent greater than 0 and at most 100");
        }
        if (maxPerCase != null && maxPerCase.signum() <= 0) throw new IllegalArgumentException("maxPerCase must be positive");
        if (maxChargesPerMonth != null && maxChargesPerMonth <= 0) throw new IllegalArgumentException("maxChargesPerMonth must be positive");
        if (effectiveFrom == null) throw new IllegalArgumentException("effectiveFrom is required");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) throw new IllegalArgumentException("effectiveTo is before effectiveFrom");
        if (legalBasisNote == null || legalBasisNote.isBlank()) {
            throw new IllegalArgumentException("A legal basis note is required: record why this fee is lawful and under which regime");
        }
        FeeRule r = new FeeRule();
        r.initTenantId(tenantId);
        r.code = code;
        r.ruleVersion = ruleVersion;
        r.description = description;
        r.calcMethod = method;
        r.fixedAmount = method == FeeCalcMethod.FIXED ? fixedAmount : null;
        r.percentOfOutstanding = method == FeeCalcMethod.PERCENT_OF_OUTSTANDING ? percent : null;
        r.maxPerCase = maxPerCase;
        r.maxChargesPerMonth = maxChargesPerMonth;
        r.effectiveFrom = effectiveFrom;
        r.effectiveTo = effectiveTo;
        r.legalBasisNote = legalBasisNote;
        r.status = FeeRuleStatus.DRAFT;
        r.createdBy = createdBy;
        return r;
    }

    /** Four-eyes: the approver must not be the author. */
    public void approve(UUID by, String byName) {
        if (status != FeeRuleStatus.DRAFT) throw new IllegalStateException("Only a DRAFT rule can be approved (status is " + status + ")");
        if (by == null || by.equals(createdBy)) {
            throw new IllegalStateException("A fee rule must be approved by someone other than its author");
        }
        this.status = FeeRuleStatus.APPROVED;
        this.approvedBy = by;
        this.approvedByName = byName;
        this.approvedAt = Instant.now();
    }

    public void supersede() {
        if (status == FeeRuleStatus.APPROVED) this.status = FeeRuleStatus.SUPERSEDED;
    }

    public void retire(String reason) {
        if (status != FeeRuleStatus.APPROVED && status != FeeRuleStatus.DRAFT) {
            throw new IllegalStateException("Cannot retire a " + status + " rule");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to retire a rule");
        this.status = FeeRuleStatus.RETIRED;
        this.retiredReason = reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    public boolean isUsableOn(LocalDate date) {
        return status == FeeRuleStatus.APPROVED && !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }
}
