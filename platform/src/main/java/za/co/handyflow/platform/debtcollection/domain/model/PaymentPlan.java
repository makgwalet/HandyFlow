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
 * A structured repayment agreement negotiated with a debtor on a
 * DebtCollectionCase. This tracks the AGREEMENT (schedule, cadence,
 * progress against it) — it does not record actual money received, which
 * stays `invoicing`'s job (an installment is expected to be paid as a
 * normal invoice payment or a new invoice/credit note, depending on how
 * the business structures it; that choice is intentionally left to the
 * service layer / business process, not hard-coded here, since it wasn't
 * specified and touches invoicing's own payment-recording logic). This
 * entity only tracks whether the debtor is keeping to what was agreed —
 * recordInstallmentDue()/markInstallmentPaid() are staff-driven based on
 * what they observe in invoicing/accounting, not an automatic payment
 * reconciliation. Flagged: a future integration could reconcile
 * automatically against InvoicingFacade.findOutstandingInvoices(), but
 * that wasn't built speculatively here.
 */
@Entity
@Table(name = "debtcollection_payment_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentPlan extends AggregateRoot<PaymentPlan> {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PaymentPlanStatus status;

    @Column(name = "total_agreed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAgreedAmount;

    @Column(name = "installment_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal installmentAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PaymentPlanFrequency frequency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(name = "number_of_installments", nullable = false)
    private Integer numberOfInstallments;

    @Column(name = "installments_paid", nullable = false)
    private Integer installmentsPaid = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    public static PaymentPlan propose(TenantId tenantId, UUID caseId, BigDecimal totalAgreedAmount,
                                       BigDecimal installmentAmount, PaymentPlanFrequency frequency,
                                       LocalDate startDate, Integer numberOfInstallments, String notes,
                                       UUID createdBy) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId is required");
        }
        if (totalAgreedAmount == null || totalAgreedAmount.signum() <= 0) {
            throw new IllegalArgumentException("totalAgreedAmount must be positive");
        }
        if (installmentAmount == null || installmentAmount.signum() <= 0) {
            throw new IllegalArgumentException("installmentAmount must be positive");
        }
        if (numberOfInstallments == null || numberOfInstallments <= 0) {
            throw new IllegalArgumentException("numberOfInstallments must be positive");
        }
        PaymentPlan p = new PaymentPlan();
        p.initTenantId(tenantId);
        p.caseId = caseId;
        p.status = PaymentPlanStatus.ACTIVE;
        p.totalAgreedAmount = totalAgreedAmount;
        p.installmentAmount = installmentAmount;
        p.frequency = frequency;
        p.startDate = startDate != null ? startDate : LocalDate.now();
        p.nextDueDate = p.startDate;
        p.numberOfInstallments = numberOfInstallments;
        p.installmentsPaid = 0;
        p.notes = notes;
        p.createdBy = createdBy;
        return p;
    }

    /**
     * Advances nextDueDate from TODAY's cadence step, not from the old due
     * date — same "don't let a late mark-off compound into an ever-earlier
     * date" choice RegulatoryObligation.markReviewed() already made.
     * Marks COMPLETED once the agreed number of installments is reached.
     */
    public void markInstallmentPaid() {
        if (status != PaymentPlanStatus.ACTIVE) {
            throw new IllegalStateException("Cannot record an installment on a plan that is not ACTIVE");
        }
        this.installmentsPaid++;
        if (this.installmentsPaid >= this.numberOfInstallments) {
            this.status = PaymentPlanStatus.COMPLETED;
            this.nextDueDate = null;
        } else {
            this.nextDueDate = switch (frequency) {
                case WEEKLY -> LocalDate.now().plusWeeks(1);
                case FORTNIGHTLY -> LocalDate.now().plusWeeks(2);
                case MONTHLY -> LocalDate.now().plusMonths(1);
            };
        }
    }

    public void markDefaulted(String reason) {
        if (status != PaymentPlanStatus.ACTIVE) {
            throw new IllegalStateException("Cannot default a plan that is not ACTIVE");
        }
        this.status = PaymentPlanStatus.DEFAULTED;
        if (reason != null && !reason.isBlank()) {
            this.notes = (this.notes == null ? "" : this.notes + "\n") + "Defaulted: " + reason;
        }
    }

    public void cancel(String reason) {
        if (status == PaymentPlanStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed plan");
        }
        this.status = PaymentPlanStatus.CANCELLED;
        if (reason != null && !reason.isBlank()) {
            this.notes = (this.notes == null ? "" : this.notes + "\n") + "Cancelled: " + reason;
        }
    }
}
