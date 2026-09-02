package za.co.handyflow.platform.collectionsagency.domain.model;

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
 * A structured repayment agreement negotiated with a debtor on a
 * CollAgencyDebtorAccount — same shape and same scope boundary as
 * debtcollection.PaymentPlan (tracks the AGREEMENT, not actual money
 * movement; actual debtor payments are recorded via
 * CollAgencyTrustTransactionService, which is what moves currentBalance
 * and trust balances).
 */
@Entity
@Table(name = "collagency_payment_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollAgencyPaymentPlan {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "debtor_account_id", nullable = false)
    private UUID debtorAccountId;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE"; // ACTIVE | COMPLETED | DEFAULTED | CANCELLED

    @Column(name = "total_agreed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAgreedAmount;

    @Column(name = "installment_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal installmentAmount;

    @Column(name = "frequency", nullable = false)
    private String frequency; // WEEKLY | FORTNIGHTLY | MONTHLY

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "number_of_installments", nullable = false)
    private Integer numberOfInstallments;

    @Column(name = "installments_paid", nullable = false)
    private Integer installmentsPaid = 0;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CollAgencyPaymentPlan propose(UUID tenantId, UUID debtorAccountId, BigDecimal totalAgreedAmount,
                                                 BigDecimal installmentAmount, String frequency, LocalDate startDate,
                                                 Integer numberOfInstallments, String notes) {
        if (debtorAccountId == null) {
            throw new IllegalArgumentException("debtorAccountId is required");
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
        if (frequency == null || frequency.isBlank()) {
            throw new IllegalArgumentException("frequency is required");
        }
        CollAgencyPaymentPlan p = new CollAgencyPaymentPlan();
        p.tenantId = tenantId;
        p.debtorAccountId = debtorAccountId;
        p.status = "ACTIVE";
        p.totalAgreedAmount = totalAgreedAmount;
        p.installmentAmount = installmentAmount;
        p.frequency = frequency;
        p.startDate = startDate != null ? startDate : LocalDate.now();
        p.nextDueDate = p.startDate;
        p.numberOfInstallments = numberOfInstallments;
        p.installmentsPaid = 0;
        p.notes = notes;
        p.createdAt = Instant.now();
        return p;
    }

    public void markInstallmentPaid() {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("Cannot record an installment on a plan that is not ACTIVE");
        }
        this.installmentsPaid++;
        if (this.installmentsPaid >= this.numberOfInstallments) {
            this.status = "COMPLETED";
            this.nextDueDate = null;
        } else {
            this.nextDueDate = switch (frequency) {
                case "WEEKLY" -> LocalDate.now().plusWeeks(1);
                case "FORTNIGHTLY" -> LocalDate.now().plusWeeks(2);
                case "MONTHLY" -> LocalDate.now().plusMonths(1);
                default -> this.nextDueDate;
            };
        }
    }

    public void markDefaulted(String reason) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("Cannot default a plan that is not ACTIVE");
        }
        this.status = "DEFAULTED";
        if (reason != null && !reason.isBlank()) {
            this.notes = (this.notes == null ? "" : this.notes + "\n") + "Defaulted: " + reason;
        }
    }

    public void cancel(String reason) {
        if ("COMPLETED".equals(status)) {
            throw new IllegalStateException("Cannot cancel a completed plan");
        }
        this.status = "CANCELLED";
        if (reason != null && !reason.isBlank()) {
            this.notes = (this.notes == null ? "" : this.notes + "\n") + "Cancelled: " + reason;
        }
    }
}
