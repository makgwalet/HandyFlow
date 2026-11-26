package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import za.co.handyflow.platform.payrollbureau.domain.rules.RunLifecycleRules;
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
    private String status = "DRAFT"; // DRAFT | CALCULATED | APPROVED | FINALISED | REVERSED

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

    @Column(name = "pay_group_id")
    private UUID payGroupId; // null = the client's default (ungrouped) payroll

    @Version
    private Long version;

    @Column(name = "calculated_by") private UUID calculatedBy;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "finalised_by") private UUID finalisedBy;
    @Column(name = "finalised_at") private Instant finalisedAt;
    @Column(name = "reversed_by") private UUID reversedBy;
    @Column(name = "reversed_at") private Instant reversedAt;
    @Column(name = "reversal_reason") private String reversalReason;
    @Column(name = "sent_to_client_at") private Instant sentToClientAt;
    @Column(name = "sent_to_client_by") private UUID sentToClientBy;
    @Column(name = "client_approved_by") private UUID clientApprovedBy;
    @Column(name = "client_approved_at") private Instant clientApprovedAt;
    @Column(name = "client_declined_at") private Instant clientDeclinedAt;
    @Column(name = "client_decline_reason") private String clientDeclineReason;

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

    public static PayRun createForGroup(UUID tenantId, UUID payClientId, UUID payGroupId, String payRunNumber,
                                        LocalDate periodStart, LocalDate periodEnd, LocalDate payDate, int taxYear) {
        PayRun r = create(tenantId, payClientId, payRunNumber, periodStart, periodEnd, payDate, taxYear);
        r.payGroupId = payGroupId;
        return r;
    }

    public java.time.Instant getPayslipsAutoSentAt() { return this.payslipsAutoSentAt; }

    public void markPayslipsAutoSent() {
        this.payslipsAutoSentAt = java.time.Instant.now();
    }

    /** Calculating a run (phase 2): its payslips exist and it waits for someone else to approve it. */
    public void complete(BigDecimal totalGross, BigDecimal totalPaye, BigDecimal totalUif,
                         BigDecimal totalSdl, BigDecimal totalNet, int employeeCount, UUID calculatedBy) {
        move(RunLifecycleRules.CALCULATED);
        this.calculatedBy = calculatedBy;
        this.totalGross = totalGross;
        this.totalPaye = totalPaye;
        this.totalUif = totalUif;
        this.totalSdl = totalSdl;
        this.totalNet = totalNet;
        this.employeeCount = employeeCount;
        this.processedAt = Instant.now();
    }

    private void move(String to) {
        String problem = RunLifecycleRules.transitionProblem(this.status, to);
        if (problem != null) throw new IllegalStateException(problem);
        this.status = to;
    }

    /** Back to a draft so items can change. The payslips are deleted by the caller; nothing has been sent. */
    public void reopen() {
        move(RunLifecycleRules.DRAFT);
        this.totalGross = null; this.totalPaye = null; this.totalUif = null; this.totalSdl = null; this.totalNet = null;
        this.employeeCount = null; this.processedAt = null; this.calculatedBy = null;
        this.approvedBy = null; this.approvedAt = null;
        this.sentToClientAt = null; this.sentToClientBy = null; this.clientApprovedBy = null; this.clientApprovedAt = null;
    }

    public void sendToClient(UUID by) {
        move(RunLifecycleRules.AWAITING_CLIENT);
        this.sentToClientAt = Instant.now(); this.sentToClientBy = by;
        this.clientDeclinedAt = null; this.clientDeclineReason = null;
    }

    public void clientApprove(UUID portalUser) {
        move(RunLifecycleRules.CLIENT_APPROVED);
        this.clientApprovedBy = portalUser; this.clientApprovedAt = Instant.now();
    }

    /** The client declines: the run goes back to a draft (the caller deletes its payslips) and the reason is kept. */
    public void clientDecline(String reason) {
        reopen();
        this.clientDeclinedAt = Instant.now(); this.clientDeclineReason = reason.trim();
    }

    public void approve(UUID approver) {
        String problem = RunLifecycleRules.approvalProblem(this.calculatedBy, approver);
        if (problem != null) throw new IllegalStateException(problem);
        move(RunLifecycleRules.APPROVED);
        this.approvedBy = approver;
        this.approvedAt = Instant.now();
    }

    public void finalise(UUID by) {
        move(RunLifecycleRules.FINALISED);
        this.finalisedBy = by;
        this.finalisedAt = Instant.now();
    }

    public void reverse(UUID by, String reason) {
        String problem = RunLifecycleRules.reversalProblem(reason);
        if (problem != null) throw new IllegalStateException(problem);
        move(RunLifecycleRules.REVERSED);
        this.reversedBy = by;
        this.reversedAt = Instant.now();
        this.reversalReason = reason.trim();
    }
}