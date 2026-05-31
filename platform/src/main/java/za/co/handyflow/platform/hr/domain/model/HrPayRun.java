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

    public void complete(BigDecimal totalGross, BigDecimal totalPaye,
                         BigDecimal totalUif, BigDecimal totalSdl,
                         BigDecimal totalNet, int employeeCount) {
        this.status        = "COMPLETED";
        this.totalGross    = totalGross;
        this.totalPaye     = totalPaye;
        this.totalUif      = totalUif;
        this.totalSdl      = totalSdl;
        this.totalNet      = totalNet;
        this.employeeCount = employeeCount;
        this.processedAt   = Instant.now();
        this.updatedAt     = Instant.now();
    }

    public void cancel() {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only DRAFT pay runs can be cancelled");
        this.status    = "CANCELLED";
        this.updatedAt = Instant.now();
    }
}