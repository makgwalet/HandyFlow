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
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of the case's recovery ledger — APPEND-ONLY, like the contact log: no setters, no deletes. A mistake is
 * fixed by a REVERSAL (of a payment) or a FEE_WAIVER (of fees), never by editing a row.
 * <p>
 * This ledger records what collection effort recovered and what fees were charged. It does NOT replace invoicing:
 * recording the money against the invoice itself stays invoicing's job. Entries carry an optional externalReference
 * (bank reference, gateway transaction id) that makes recording idempotent per tenant.
 * <p>
 * Effect on balances: PAYMENT adds to recovered (split into allocatedPrincipal + allocatedFees); REVERSAL undoes
 * exactly one PAYMENT (copying its split); FEE_CHARGE adds to fees owed; FEE_WAIVER reduces fees owed.
 */
@Entity
@Table(name = "debtcollection_recovery_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecoveryEntry extends AggregateRoot<RecoveryEntry> {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 15)
    private LedgerEntryType entryType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "allocated_principal", nullable = false, precision = 15, scale = 2)
    private BigDecimal allocatedPrincipal = BigDecimal.ZERO;

    @Column(name = "allocated_fees", nullable = false, precision = 15, scale = 2)
    private BigDecimal allocatedFees = BigDecimal.ZERO;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private PaymentSource source;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "reversal_of_id")
    private UUID reversalOfId;

    @Column(name = "fee_rule_code", length = 60)
    private String feeRuleCode;

    @Column(name = "fee_rule_version")
    private Integer feeRuleVersion;

    @Column(name = "ptp_id")
    private UUID ptpId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "recorded_by_user_name", length = 255)
    private String recordedByUserName;

    public static RecoveryEntry payment(TenantId tenantId, UUID caseId, BigDecimal amount, BigDecimal principal,
                                        BigDecimal fees, LocalDate date, PaymentSource source, String externalReference,
                                        UUID ptpId, String notes, UUID by, String byName) {
        positive(amount, "amount");
        if (principal == null || fees == null || principal.signum() < 0 || fees.signum() < 0
                || principal.add(fees).compareTo(amount) != 0) {
            throw new IllegalArgumentException("allocatedPrincipal + allocatedFees must equal the payment amount");
        }
        if (source == null) throw new IllegalArgumentException("source is required");
        RecoveryEntry e = base(tenantId, caseId, LedgerEntryType.PAYMENT, amount, date, notes, by, byName);
        e.allocatedPrincipal = principal;
        e.allocatedFees = fees;
        e.source = source;
        e.externalReference = externalReference;
        e.ptpId = ptpId;
        return e;
    }

    public static RecoveryEntry reversalOf(TenantId tenantId, RecoveryEntry original, LocalDate date, String reason,
                                           UUID by, String byName) {
        if (original.entryType != LedgerEntryType.PAYMENT) throw new IllegalArgumentException("Only a PAYMENT can be reversed");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to reverse a payment");
        RecoveryEntry e = base(tenantId, original.caseId, LedgerEntryType.REVERSAL, original.amount, date, reason, by, byName);
        e.allocatedPrincipal = original.allocatedPrincipal;
        e.allocatedFees = original.allocatedFees;
        e.reversalOfId = original.getId();
        e.source = original.source;
        e.ptpId = original.ptpId;
        return e;
    }

    public static RecoveryEntry feeCharge(TenantId tenantId, UUID caseId, BigDecimal amount, String ruleCode,
                                          int ruleVersion, LocalDate date, String notes, UUID by, String byName) {
        positive(amount, "amount");
        if (ruleCode == null || ruleCode.isBlank()) throw new IllegalArgumentException("A fee rule is required");
        RecoveryEntry e = base(tenantId, caseId, LedgerEntryType.FEE_CHARGE, amount, date, notes, by, byName);
        e.feeRuleCode = ruleCode;
        e.feeRuleVersion = ruleVersion;
        return e;
    }

    public static RecoveryEntry feeWaiver(TenantId tenantId, UUID caseId, BigDecimal amount, LocalDate date, String reason,
                                          UUID by, String byName) {
        positive(amount, "amount");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to waive fees");
        return base(tenantId, caseId, LedgerEntryType.FEE_WAIVER, amount, date, reason, by, byName);
    }

    private static RecoveryEntry base(TenantId tenantId, UUID caseId, LedgerEntryType type, BigDecimal amount,
                                      LocalDate date, String notes, UUID by, String byName) {
        if (caseId == null) throw new IllegalArgumentException("caseId is required");
        RecoveryEntry e = new RecoveryEntry();
        e.initTenantId(tenantId);
        e.caseId = caseId;
        e.entryType = type;
        e.amount = amount;
        e.effectiveDate = date != null ? date : LocalDate.now();
        e.notes = notes;
        e.recordedByUserId = by;
        e.recordedByUserName = byName;
        return e;
    }

    private static void positive(BigDecimal v, String name) {
        if (v == null || v.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
