package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A bonus or back pay owed to one employee. Never edited or deleted; a wrong one is cancelled with a reason. */
@Entity
@Table(name = "hr_pay_adjustments")
@Getter
@NoArgsConstructor
public class HrPayAdjustment {

    @Id UUID id;
    @Column(name = "tenant_id")     UUID tenantId;
    @Column(name = "employee_id")   UUID employeeId;
    String kind;
    BigDecimal amount;
    @Column(name = "pay_from")      LocalDate payFrom;
    String description;
    @Column(name = "relates_from")  LocalDate relatesFrom;
    @Column(name = "relates_to")    LocalDate relatesTo;
    String reason;
    String status;
    /** The pay run that has taken it, if any. */
    @Column(name = "pay_run_id")    UUID payRunId;
    @Column(name = "created_by")    UUID createdBy;
    @Column(name = "created_at")    Instant createdAt;
    @Column(name = "cancelled_by")  UUID cancelledBy;
    @Column(name = "cancelled_at")  Instant cancelledAt;
    @Column(name = "cancel_reason") String cancelReason;

    public static HrPayAdjustment of(UUID tenantId, UUID employeeId, String kind, BigDecimal amount, LocalDate payFrom, String description,
                                     LocalDate relatesFrom, LocalDate relatesTo, String reason, UUID by) {
        HrPayAdjustment a = new HrPayAdjustment();
        a.id = UUID.randomUUID();
        a.tenantId = tenantId;
        a.employeeId = employeeId;
        a.kind = kind;
        a.amount = amount;
        a.payFrom = payFrom;
        a.description = description.trim();
        a.relatesFrom = PayAdjustmentRules.BACK_PAY.equals(kind) ? relatesFrom : null;
        a.relatesTo = PayAdjustmentRules.BACK_PAY.equals(kind) ? relatesTo : null;
        a.reason = reason.trim();
        a.status = PayAdjustmentRules.OPEN;
        a.createdBy = by;
        a.createdAt = Instant.now();
        return a;
    }

    public void cancel(UUID by, String reason) {
        this.status = PayAdjustmentRules.CANCELLED;
        this.cancelledBy = by;
        this.cancelledAt = Instant.now();
        this.cancelReason = reason.trim();
    }

    public void linkTo(UUID payRunId) { this.payRunId = payRunId; }
}
