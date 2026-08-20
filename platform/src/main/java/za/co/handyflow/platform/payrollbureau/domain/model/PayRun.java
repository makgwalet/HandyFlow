package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One payroll run for one client, for one period. Mirrors HrPayRun's
 * role — DRAFT -> PROCESSED lifecycle, aggregate totals computed once
 * all payslips in the run are calculated.
 */
@Entity
@Table(name = "pay_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayRun {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pay_client_id", nullable = false)
    private UUID payClientId;

    @Column(name = "pay_run_number", nullable = false)
    private String payRunNumber;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT"; // DRAFT | PROCESSED

    @Column(name = "total_gross", precision = 15, scale = 2)
    private BigDecimal totalGross;

    @Column(name = "total_paye", precision = 15, scale = 2)
    private BigDecimal totalPaye;

    @Column(name = "total_uif", precision = 15, scale = 2)
    private BigDecimal totalUif;

    @Column(name = "total_sdl", precision = 15, scale = 2)
    private BigDecimal totalSdl;

    @Column(name = "total_net", precision = 15, scale = 2)
    private BigDecimal totalNet;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "payslips_auto_sent_at")
    private java.time.Instant payslipsAutoSentAt;

    public static PayRun create(UUID tenantId, UUID payClientId, String payRunNumber,
                                LocalDate periodStart, LocalDate periodEnd, LocalDate payDate, int taxYear) {
        PayRun r = new PayRun();
        r.tenantId = tenantId;
        r.payClientId = payClientId;
        r.payRunNumber = payRunNumber;
        r.periodStart = periodStart;
        r.periodEnd = periodEnd;
        r.payDate = payDate;
        r.taxYear = taxYear;
        r.status = "DRAFT";
        r.createdAt = Instant.now();
        return r;
    }

    public java.time.Instant getPayslipsAutoSentAt() { return this.payslipsAutoSentAt; }

    public void markPayslipsAutoSent() {
        this.payslipsAutoSentAt = java.time.Instant.now();
    }

    public void complete(BigDecimal totalGross, BigDecimal totalPaye, BigDecimal totalUif,
                         BigDecimal totalSdl, BigDecimal totalNet, int employeeCount) {
        if (!"DRAFT".equals(this.status)) {
            throw new IllegalStateException("Only DRAFT pay runs can be processed");
        }
        this.status = "PROCESSED";
        this.totalGross = totalGross;
        this.totalPaye = totalPaye;
        this.totalUif = totalUif;
        this.totalSdl = totalSdl;
        this.totalNet = totalNet;
        this.employeeCount = employeeCount;
        this.processedAt = Instant.now();
    }
}