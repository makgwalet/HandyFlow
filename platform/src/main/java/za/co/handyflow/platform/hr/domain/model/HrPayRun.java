package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "hr_pay_runs")
@Getter
@NoArgsConstructor
public class HrPayRun {

    @Id UUID id;
    @Column(name = "tenant_id")      UUID tenantId;
    @Column(name = "pay_run_number") String payRunNumber;
    @Column(name = "period_start")   LocalDate periodStart;
    @Column(name = "period_end")     LocalDate periodEnd;
    @Column(name = "pay_date")       LocalDate payDate;
    @Column(name = "tax_year")       int taxYear;
    String status = "DRAFT";
    @Column(name = "total_gross")    BigDecimal totalGross    = BigDecimal.ZERO;
    @Column(name = "total_paye")     BigDecimal totalPaye     = BigDecimal.ZERO;
    @Column(name = "total_uif")      BigDecimal totalUif      = BigDecimal.ZERO;
    @Column(name = "total_sdl")      BigDecimal totalSdl      = BigDecimal.ZERO;
    @Column(name = "total_net")      BigDecimal totalNet      = BigDecimal.ZERO;
    @Column(name = "employee_count") int employeeCount;
    String notes;
    @Column(name = "processed_at")   Instant processedAt;
    @Column(name = "prepared_by")    UUID preparedBy;
    @Column(name = "prepared_at")    Instant preparedAt;
    @Column(name = "approved_by")    UUID approvedBy;
    @Column(name = "approved_at")    Instant approvedAt;
    @Column(name = "finalised_by")   UUID finalisedBy;
    @Column(name = "finalised_at")   Instant finalisedAt;
    @Column(name = "created_at")     Instant createdAt;
    @Column(name = "updated_at")     Instant updatedAt;

    public static HrPayRun create(TenantId tenantId, String payRunNumber,
                                  LocalDate periodStart, LocalDate periodEnd,
                                  LocalDate payDate, int taxYear) {
        HrPayRun r = new HrPayRun();
        r.id            = UUID.randomUUID();
        r.tenantId      = tenantId.getValue();
        r.payRunNumber  = payRunNumber;
        r.periodStart   = periodStart;
        r.periodEnd     = periodEnd;
        r.payDate       = payDate;
        r.taxYear       = taxYear;
        r.status        = "DRAFT";
        r.totalGross    = BigDecimal.ZERO;
        r.totalPaye     = BigDecimal.ZERO;
        r.totalUif      = BigDecimal.ZERO;
        r.totalSdl      = BigDecimal.ZERO;
        r.totalNet      = BigDecimal.ZERO;
        r.employeeCount = 0;
        r.createdAt     = Instant.now();
        r.updatedAt     = Instant.now();
        return r;
    }

    public void markProcessing() {
        this.status    = "PROCESSING";
        this.updatedAt = Instant.now();
    }

    /** The calculation is done. The run now waits for a second person; nobody but staff sees its payslips yet. */
    public void prepared(BigDecimal totalGross, BigDecimal totalPaye,
                         BigDecimal totalUif, BigDecimal totalSdl,
                         BigDecimal totalNet, int employeeCount, UUID actor) {
        this.status        = PayRunWorkflowRules.PREPARED;
        this.totalGross    = totalGross;
        this.totalPaye     = totalPaye;
        this.totalUif      = totalUif;
        this.totalSdl      = totalSdl;
        this.totalNet      = totalNet;
        this.employeeCount = employeeCount;
        this.processedAt   = Instant.now();
        this.preparedBy    = actor;
        this.preparedAt    = this.processedAt;
        this.approvedBy    = null;
        this.approvedAt    = null;
        this.updatedAt     = Instant.now();
    }

    public void approve(UUID actor) {
        this.status     = PayRunWorkflowRules.APPROVED;
        this.approvedBy = actor;
        this.approvedAt = Instant.now();
        this.updatedAt  = Instant.now();
    }

    /** Back to a draft so it can be corrected and calculated again. Earlier approval is cleared. */
    public void sendBack() {
        this.status     = PayRunWorkflowRules.DRAFT;
        this.approvedBy = null;
        this.approvedAt = null;
        this.updatedAt  = Instant.now();
    }

    public void finalise(UUID actor) {
        this.status      = PayRunWorkflowRules.FINALISED;
        this.finalisedBy = actor;
        this.finalisedAt = Instant.now();
        this.updatedAt   = Instant.now();
    }

    public void cancel() {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only DRAFT pay runs can be cancelled");
        this.status    = "CANCELLED";
        this.updatedAt = Instant.now();
    }
}