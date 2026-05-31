package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollService {

    private final HrPayRunRepository    payRunRepo;
    private final HrPayslipRepository   payslipRepo;
    private final HrEmp201Repository    emp201Repo;
    private final HrEmployeeRepository  employeeRepo;
    private final PayrollEngine         engine;
    private final PayRunNumberGenerator numberGen;

    // ── Pay runs ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PayRunResponse> getPayRuns(TenantId tenantId, Pageable pageable) {
        return payRunRepo.findAllByTenant(tenantId, pageable)
                .map(this::toPayRunResponse);
    }

    @Transactional
    public PayRunResponse createPayRun(TenantId tenantId, CreatePayRunRequest req) {
        // WHY determine tax year? SA tax year runs Mar–Feb.
        // March 2026 is tax year 2026. February 2026 is tax year 2025.
        int taxYear = req.periodStart().getMonthValue() >= 3
                ? req.periodStart().getYear()
                : req.periodStart().getYear() - 1;

        String number = numberGen.next(tenantId, req.periodStart());
        HrPayRun run = HrPayRun.create(tenantId, number,
                req.periodStart(), req.periodEnd(), req.payDate(), taxYear);
        if (req.notes() != null) {
            try {
                java.lang.reflect.Field f = run.getClass().getDeclaredField("notes");
                f.setAccessible(true);
                f.set(run, req.notes());
            } catch (Exception ignored) {}
        }
        payRunRepo.save(run);
        log.info("Created pay run={} period={} to {}", number,
                req.periodStart(), req.periodEnd());
        return toPayRunResponse(run);
    }

    @Transactional
    public PayRunResponse processPayRun(TenantId tenantId, UUID payRunId) {
        HrPayRun run = payRunRepo.findByTenantAndId(tenantId, payRunId)
                .orElseThrow(() -> new ResourceNotFoundException("PayRun", payRunId.toString()));

        if (!"DRAFT".equals(run.getStatus()))
            throw new IllegalStateException("Only DRAFT pay runs can be processed");

        run.markProcessing();
        payRunRepo.save(run);

        List<HrEmployee> employees = employeeRepo.findAllActiveList(tenantId);
        if (employees.isEmpty())
            throw new IllegalStateException("No active employees found for this tenant");

        // Calculate annual payroll for SDL threshold check
        BigDecimal annualPayroll = employees.stream()
                .map(HrEmployee::getGrossSalary)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(12));

        BigDecimal runGross = BigDecimal.ZERO;
        BigDecimal runPaye  = BigDecimal.ZERO;
        BigDecimal runUif   = BigDecimal.ZERO;
        BigDecimal runSdl   = BigDecimal.ZERO;
        BigDecimal runNet   = BigDecimal.ZERO;

        for (HrEmployee emp : employees) {
            PayrollEngine.PayrollResult result =
                    engine.calculate(emp, run.getTaxYear(), annualPayroll);

            HrPayslip slip = HrPayslip.create(tenantId, run.getId(), emp.getId(),
                    result.grossSalary(), result.travelAllowance(),
                    result.medicalAid(), result.pension());

            slip.applyCalculations(
                    result.payeAmount(), result.uifEmployee(), result.uifEmployer(),
                    result.sdlAmount(), result.taxableIncome(), result.taxBeforeRebate(),
                    result.primaryRebate(), run.getTaxYear());

            // YTD calculations
            // YTD calculations
            BigDecimal ytdGross = payslipRepo.sumYtdGross(
                    emp.getId(), run.getTaxYear(), slip.getId());
            BigDecimal ytdPaye  = payslipRepo.sumYtdPaye(
                    emp.getId(), run.getTaxYear(), slip.getId());
            BigDecimal ytdUif   = payslipRepo.sumYtdUif(
                    emp.getId(), run.getTaxYear(), slip.getId());

            try {
                java.lang.reflect.Field ytdGrossF =
                        slip.getClass().getDeclaredField("ytdGross");
                java.lang.reflect.Field ytdPayeF  =
                        slip.getClass().getDeclaredField("ytdPaye");
                java.lang.reflect.Field ytdUifF   =
                        slip.getClass().getDeclaredField("ytdUif");
                ytdGrossF.setAccessible(true);
                ytdPayeF.setAccessible(true);
                ytdUifF.setAccessible(true);
                ytdGrossF.set(slip, ytdGross.add(result.totalEarnings()));
                ytdPayeF.set(slip,  ytdPaye.add(result.payeAmount()));
                ytdUifF.set(slip,   ytdUif.add(result.uifEmployee()));
            } catch (Exception ignored) {}

            payslipRepo.save(slip);

            runGross = runGross.add(result.totalEarnings());
            runPaye  = runPaye.add(result.payeAmount());
            runUif   = runUif.add(result.uifEmployee()).add(result.uifEmployer());
            runSdl   = runSdl.add(result.sdlAmount());
            runNet   = runNet.add(result.netPay());
        }

        run.complete(runGross, runPaye, runUif, runSdl, runNet, employees.size());
        payRunRepo.save(run);

        // Auto-generate EMP201
        HrEmp201 emp201 = HrEmp201.create(tenantId, run.getId(),
                run.getPeriodStart(), run.getPeriodEnd(),
                runPaye, runUif, runSdl);
        emp201Repo.save(emp201);

        log.info("Processed pay run={} employees={} gross={} paye={} net={}",
                run.getPayRunNumber(), employees.size(), runGross, runPaye, runNet);
        return toPayRunResponse(run);
    }

    // ── Payslips ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PayslipResponse> getPayRunPayslips(TenantId tenantId, UUID payRunId) {
        payRunRepo.findByTenantAndId(tenantId, payRunId)
                .orElseThrow(() -> new ResourceNotFoundException("PayRun", payRunId.toString()));
        return payslipRepo.findByPayRun(payRunId)
                .stream().map(s -> toPayslipResponse(s, tenantId)).toList();
    }

    @Transactional(readOnly = true)
    public List<PayslipResponse> getEmployeePayslips(TenantId tenantId, UUID employeeId) {
        employeeRepo.findActiveById(tenantId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId.toString()));
        return payslipRepo.findByEmployee(employeeId)
                .stream().map(s -> toPayslipResponse(s, tenantId)).toList();
    }

    // ── EMP201 ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Emp201Response> getEmp201s(TenantId tenantId) {
        return emp201Repo.findAllByTenant(tenantId)
                .stream().map(this::toEmp201Response).toList();
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private PayRunResponse toPayRunResponse(HrPayRun r) {
        return new PayRunResponse(r.getId(), r.getPayRunNumber(),
                r.getPeriodStart(), r.getPeriodEnd(), r.getPayDate(),
                r.getTaxYear(), r.getStatus(), r.getTotalGross(), r.getTotalPaye(),
                r.getTotalUif(), r.getTotalSdl(), r.getTotalNet(),
                r.getEmployeeCount(), r.getNotes(), r.getProcessedAt(), r.getCreatedAt());
    }

    private PayslipResponse toPayslipResponse(HrPayslip s, TenantId tenantId) {
        HrEmployee emp = employeeRepo.findActiveById(tenantId, s.getEmployeeId())
                .orElse(null);
        String empName   = emp != null ? emp.getFullName()       : "Unknown";
        String empNumber = emp != null ? emp.getEmployeeNumber() : "—";
        String runNumber = payRunRepo.findById(s.getPayRunId())
                .map(HrPayRun::getPayRunNumber).orElse("—");
        return new PayslipResponse(s.getId(), s.getEmployeeId(), empName, empNumber,
                s.getPayRunId(), runNumber, s.getGrossSalary(), s.getOvertimeAmount(),
                s.getBonusAmount(), s.getTravelAllowance(), s.getTotalEarnings(),
                s.getPayeAmount(), s.getUifEmployee(), s.getMedicalAid(), s.getPension(),
                s.getTotalDeductions(), s.getUifEmployer(), s.getSdlAmount(),
                s.getNetPay(), s.getYtdGross(), s.getYtdPaye(),
                s.getTaxableIncome(), s.getTaxYear() != null ? s.getTaxYear() : 0,
                s.getCreatedAt());
    }

    private Emp201Response toEmp201Response(HrEmp201 e) {
        return new Emp201Response(e.getId(), e.getPayRunId(),
                e.getPeriodStart(), e.getPeriodEnd(), e.getDueDate(),
                e.getTotalPaye(), e.getTotalUif(), e.getTotalSdl(),
                e.getTotalPayable(), e.getStatus(), e.getSubmittedAt(), e.getCreatedAt());
    }
}