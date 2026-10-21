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
import za.co.handyflow.platform.shared.StatutoryYearRules;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
    private final StatutoryRulesService rulesService;
    private final MedicalSchemeService  medicalSchemeService;
    private final HrPayRunEventRepository eventRepo;
    private final EmployeeChangeService changeService;
    private final OvertimeService       overtimeService;

    // ── Pay runs ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PayRunResponse> getPayRuns(TenantId tenantId, Pageable pageable) {
        return payRunRepo.findAllByTenant(tenantId, pageable)
                .map(r -> toPayRunResponse(r, List.of()));
    }

    @Transactional
    public PayRunResponse createPayRun(TenantId tenantId, CreatePayRunRequest req) {
        if (req.periodEnd().isBefore(req.periodStart()))
            throw new IllegalArgumentException("The period cannot end before it starts.");
        if (req.payDate().isBefore(req.periodStart()))
            throw new IllegalArgumentException("The pay date cannot be before the period starts.");

        // The tax year is named by the year it ends, as SARS names it, and is taken from the pay date:
        // 1 March 2026 to 28 February 2027 is tax year 2027. A run is refused up front when that year has no
        // approved rules, rather than producing payslips with no tax.
        int taxYear = StatutoryYearRules.taxYearOf(req.payDate());
        rulesService.rulesFor(taxYear);

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
        return toPayRunResponse(run, List.of());
    }

    @Transactional
    public PayRunResponse processPayRun(TenantId tenantId, UUID payRunId, UUID actor) {
        HrPayRun run = payRunRepo.findByTenantAndId(tenantId, payRunId)
                .orElseThrow(() -> new ResourceNotFoundException("PayRun", payRunId.toString()));

        PayRunWorkflowRules.checkCanPrepare(run.getStatus());
        String before = run.getStatus();

        // The tax year comes from the pay date, not from the label the run was created with: runs made before
        // this fix can carry a wrong label. The rules are loaded once, and a missing year stops the run before
        // anything is deleted or written.
        int taxYear = StatutoryYearRules.taxYearOf(run.getPayDate());
        StatutoryYearRules rules = rulesService.rulesFor(taxYear);

        // FIX 1.4: Idempotency — delete any existing payslips before re-processing
        // Prevents duplicate payslips if processing was retried after a partial failure
        List<HrPayslip> existing = payslipRepo.findByPayRun(payRunId);
        if (!existing.isEmpty()) {
            log.warn("Deleting {} existing payslips before re-processing pay run={}",
                    existing.size(), run.getPayRunNumber());
            payslipRepo.deleteAll(existing);
        }

        // A recalculation replaces the EMP201 draft too. One that has already moved on is not touched.
        List<HrEmp201> oldEmp201 = emp201Repo.forPayRun(tenantId, run.getId());
        if (oldEmp201.stream().anyMatch(e -> !"DRAFT".equals(e.getStatus())))
            throw new IllegalStateException("The EMP201 of this run has already been submitted, so the run cannot be recalculated.");
        emp201Repo.deleteAll(oldEmp201);

        run.markProcessing();
        payRunRepo.save(run);

        // Approved pay and bank changes dated up to the end of the period take effect before anything is calculated.
        changeService.applyDue(tenantId, run.getPeriodEnd());

        List<HrEmployee> employees = employeeRepo.findAllActiveList(tenantId);
        if (employees.isEmpty())
            throw new IllegalStateException("No active employees found for this tenant");

        List<String> warnings = new java.util.ArrayList<>();
        if (taxYear != run.getTaxYear())
            warnings.add("This run was labelled tax year " + run.getTaxYear() + " but its pay date falls in " + taxYear
                    + ". It was calculated under " + taxYear + ".");

        Map<UUID, MedicalSchemeRules.Members> members = medicalSchemeService.membersOn(tenantId, employees, run.getPayDate());

        BigDecimal annualPayroll = employees.stream()
                .map(HrEmployee::getGrossSalary)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(12));

        // Approved overtime worked up to the end of the period that no other run has taken. Taken again from scratch on a recalculation.
        Map<UUID, List<HrOvertimeClaim>> overtime = overtimeService.takeFor(tenantId, run.getId(), run.getPeriodEnd());
        int waiting = overtimeService.waitingCount(tenantId, run.getPeriodEnd());
        if (waiting > 0)
            warnings.add(waiting + " overtime claim(s) worked in this period are still waiting for approval, so they are not in this run. "
                    + "They will be paid in a later run once approved.");
        if (!overtime.isEmpty())
            warnings.add("Overtime for " + overtime.size() + " employee(s) is included. PAYE treats each month's overtime as if it recurred every month "
                    + "(the periodic method), so it can be higher than the year turns out to need; it is corrected when the year is assessed.");

        BigDecimal runGross = BigDecimal.ZERO, runPaye = BigDecimal.ZERO;
        BigDecimal runUif   = BigDecimal.ZERO, runSdl  = BigDecimal.ZERO;
        BigDecimal runNet   = BigDecimal.ZERO;

        for (HrEmployee emp : employees) {
            MedicalSchemeRules.Members m = members.get(emp.getId());
            if (m.assumed())
                warnings.add(emp.getFullName() + " (" + emp.getEmployeeNumber() + "): no medical scheme membership is on record, "
                        + "so the medical credit assumes the employee only. Record the membership to confirm it.");
            List<HrOvertimeClaim> claims = overtime.getOrDefault(emp.getId(), List.of());
            BigDecimal overtimePay = claims.stream().map(HrOvertimeClaim::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            PayrollEngine.PayrollResult result = engine.calculate(emp, rules, annualPayroll, m.count(), overtimePay);

            HrPayslip slip = HrPayslip.create(tenantId, run.getId(), emp.getId(),
                    result.grossSalary(), result.travelAllowance(),
                    result.medicalAid(), result.pension());
            slip.setOvertimeAmount(overtimePay);

            // FIX 1.5: Calculate YTD BEFORE save — slip.getId() is not yet in DB
            BigDecimal ytdGross = payslipRepo.sumYtdGross(emp.getId(), taxYear, slip.getId());
            BigDecimal ytdPaye  = payslipRepo.sumYtdPaye(emp.getId(), taxYear, slip.getId());
            BigDecimal ytdUif   = payslipRepo.sumYtdUif(emp.getId(), taxYear, slip.getId());

            slip.applyCalculations(
                    result.payeAmount(), result.uifEmployee(), result.uifEmployer(),
                    result.sdlAmount(), result.taxableIncome(), result.taxBeforeRebate(),
                    result.primaryRebate(), taxYear);

            // The credit taken off this month's PAYE, kept for the tax certificate (code 4116). It cannot exceed the
            // tax that is left after the rebates.
            BigDecimal taxAfterRebates = result.taxBeforeRebate().subtract(result.totalRebates()).max(BigDecimal.ZERO);
            slip.setMedicalTaxCredit(result.medicalTaxCredit().min(taxAfterRebates)
                    .divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP));

            // FIX 1.2: Replace reflection with direct setters on HrPayslip
            slip.setYtdGross(ytdGross.add(result.totalEarnings()));
            slip.setYtdPaye(ytdPaye.add(result.payeAmount()));
            slip.setYtdUif(ytdUif.add(result.uifEmployee()));

            payslipRepo.save(slip);
            overtimeService.markTaken(claims, run.getId());

            runGross = runGross.add(result.totalEarnings());
            runPaye  = runPaye.add(result.payeAmount());
            runUif   = runUif.add(result.uifEmployee()).add(result.uifEmployer());
            runSdl   = runSdl.add(result.sdlAmount());
            runNet   = runNet.add(result.netPay());
        }

        run.prepared(runGross, runPaye, runUif, runSdl, runNet, employees.size(), actor);
        payRunRepo.save(run);
        eventRepo.save(HrPayRunEvent.of(tenantId.getValue(), run.getId(), "CALCULATED", before, run.getStatus(), actor,
                employees.size() + " payslips" + (warnings.isEmpty() ? "" : ", " + warnings.size() + " warnings")));

        HrEmp201 emp201 = HrEmp201.create(tenantId, run.getId(),
                run.getPeriodStart(), run.getPeriodEnd(), runPaye, runUif, runSdl);
        emp201Repo.save(emp201);

        log.info("Processed pay run={} taxYear={} employees={} gross={} paye={} uif={} sdl={} net={} warnings={}",
                run.getPayRunNumber(), taxYear, employees.size(), runGross, runPaye, runUif, runSdl, runNet, warnings.size());
        return toPayRunResponse(run, warnings);
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
        return getEmployeePayslips(tenantId, employeeId, false);
    }

    /**
     * @param issuedOnly true for what an employee may see: payslips of finalised runs, and of runs issued before approval
     *                   existed. Payslips still being checked are not shown to the employee.
     */
    @Transactional(readOnly = true)
    public List<PayslipResponse> getEmployeePayslips(TenantId tenantId, UUID employeeId, boolean issuedOnly) {
        employeeRepo.findActiveById(tenantId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId.toString()));
        List<HrPayslip> slips = payslipRepo.findByEmployee(employeeId);
        if (issuedOnly && !slips.isEmpty()) {
            Map<UUID, String> status = new java.util.HashMap<>();
            payRunRepo.findAllById(slips.stream().map(HrPayslip::getPayRunId).distinct().toList())
                    .forEach(r -> status.put(r.getId(), r.getStatus()));
            slips = slips.stream().filter(sl -> PayRunWorkflowRules.isIssued(status.get(sl.getPayRunId()))).toList();
        }
        return slips.stream().map(sl -> toPayslipResponse(sl, tenantId)).toList();
    }

    // ── EMP201 ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Emp201Response> getEmp201s(TenantId tenantId) {
        return emp201Repo.findAllByTenant(tenantId)
                .stream().map(this::toEmp201Response).toList();
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private PayRunResponse toPayRunResponse(HrPayRun r, List<String> warnings) {
        return new PayRunResponse(r.getId(), r.getPayRunNumber(),
                r.getPeriodStart(), r.getPeriodEnd(), r.getPayDate(),
                r.getTaxYear(), r.getStatus(), r.getTotalGross(), r.getTotalPaye(),
                r.getTotalUif(), r.getTotalSdl(), r.getTotalNet(),
                r.getEmployeeCount(), r.getNotes(), r.getProcessedAt(), r.getCreatedAt(),
                r.getPreparedBy(), r.getPreparedAt(), r.getApprovedBy(), r.getApprovedAt(), r.getFinalisedBy(), r.getFinalisedAt(), warnings);
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