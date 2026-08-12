package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A SARS filing deadline for one of the bureau's payroll clients.
 * Deliberately a SEPARATE entity/engine from accountant.TaxDeadline —
 * see PayDeadlineEngine's own Javadoc for why this isn't a retrofit of
 * DeadlineEngine to accept two client types.
 */
@Entity
@Table(name = "pay_deadlines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayDeadline {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pay_client_id", nullable = false)
    private UUID payClientId;

    @Column(name = "deadline_type", nullable = false)
    private String deadlineType; // EMP201 | EMP501

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month")
    private Integer periodMonth; // null for EMP501 (annual)

    @Column(name = "raw_due_date", nullable = false)
    private LocalDate rawDueDate;

    @Column(name = "adjusted_due_date", nullable = false)
    private LocalDate adjustedDueDate;

    @Column(name = "status", nullable = false)
    private String status = "PENDING"; // PENDING | FILED | OVERDUE

    @Column(name = "filed_date")
    private LocalDate filedDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PayDeadline create(UUID tenantId, UUID payClientId, String deadlineType,
                                     int periodYear, Integer periodMonth,
                                     LocalDate rawDueDate, LocalDate adjustedDueDate) {
        PayDeadline d = new PayDeadline();
        d.tenantId = tenantId;
        d.payClientId = payClientId;
        d.deadlineType = deadlineType;
        d.periodYear = periodYear;
        d.periodMonth = periodMonth;
        d.rawDueDate = rawDueDate;
        d.adjustedDueDate = adjustedDueDate;
        d.status = "PENDING";
        d.createdAt = Instant.now();
        return d;
    }

    public void markFiled(LocalDate filedDate) {
        this.status = "FILED";
        this.filedDate = filedDate;
    }
}