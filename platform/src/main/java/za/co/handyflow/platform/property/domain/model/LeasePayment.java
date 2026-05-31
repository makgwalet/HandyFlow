// property/domain/model/LeasePayment.java

package za.co.handyflow.platform.property.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lease_payments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LeasePayment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "lease_id", nullable = false)
    private UUID leaseId;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "amount_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "payment_method")
    private String paymentMethod;

    private String reference;

    @Column(nullable = false)
    private String status = "PENDING";

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static LeasePayment create(TenantId tenantId, UUID leaseId,
                                      int periodYear, int periodMonth,
                                      BigDecimal amountDue, LocalDate dueDate) {
        LeasePayment p = new LeasePayment();
        p.tenantId    = tenantId;
        p.leaseId     = leaseId;
        p.periodYear  = periodYear;
        p.periodMonth = periodMonth;
        p.amountDue   = amountDue;
        p.amountPaid  = BigDecimal.ZERO;
        p.dueDate     = dueDate;
        p.status      = "PENDING";
        p.createdAt   = Instant.now();
        p.updatedAt   = Instant.now();
        return p;
    }

    public void recordPayment(BigDecimal amount, LocalDate paidDate,
                              String paymentMethod, String reference) {
        this.amountPaid    = this.amountPaid.add(amount);
        this.paidDate      = paidDate;
        this.paymentMethod = paymentMethod;
        this.reference     = reference;
        this.updatedAt     = Instant.now();

        // WHY computed status? Avoids stale status bugs
        if (this.amountPaid.compareTo(this.amountDue) >= 0) {
            this.status = "PAID";
        } else if (this.amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            this.status = "PARTIAL";
        }
    }

    public void markOverdue() {
        if ("PENDING".equals(status) || "PARTIAL".equals(status)) {
            this.status    = "OVERDUE";
            this.updatedAt = Instant.now();
        }
    }

    public void waive(String reason) {
        this.status    = "WAIVED";
        this.notes     = reason;
        this.updatedAt = Instant.now();
    }

    public BigDecimal getBalance() {
        return amountDue.subtract(amountPaid);
    }

    public boolean isFullyPaid() {
        return "PAID".equals(status);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}