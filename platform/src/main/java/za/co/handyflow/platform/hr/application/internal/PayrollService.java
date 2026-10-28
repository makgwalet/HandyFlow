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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final za.co.handyflow.platform.shared.PublicHolidayRepository holidayRepo;
    private final HrLeaveRequestRepository leaveRepo;
    private final LeaveService          leaveService;
    private final BenefitService        benefitService;
    private final PayAdjustmentService  payAdjustmentService;
    /** Optional so older constructions still work; no settings means the periodic method. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    HrPayrollSettingsRepository settingsRepo;

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

        List<HrEmployee> active = employeeRepo.findAllActiveList(tenantId);
        // Leavers are paid up to the day they left, and a person who joins part way through the period is paid from their start date.
        List<HrEmployee> employees = new java.util.ArrayList<>();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (HrEmployee e : active) if (seen.add(e.getId())) employees.add(e);
        for (HrEmployee e : employeeRepo.terminatedForPay(tenantId, run.getPeriodEnd(), PayPeriodRules.earliestLateLeaver(run.getPeriodStart()), run.getId()))
            if (seen.add(e.getId())) employees.add(e);
        employees.removeIf(e -> e.getStartDate() != null && e.getStartDate().isAfter(run.getPeriodEnd()));
        if (employees.isEmpty())
            throw new IllegalStateException("No active employees found for this tenant");

        java.util.Set<LocalDate> holidays = new java.util.HashSet<>(holidayRepo.datesBetween(run.getPeriodStart(), run.getPeriodEnd()));
        Map<UUID, BigDecimal> unpaidDays = new java.util.HashMap<>();
        for (HrLeaveRequest r : leaveRepo.approvedUnpaidIn(tenantId, run.getPeriodStart(), run.getPeriodEnd()))
            unpaidDays.merge(r.getEmployeeId(), PayPeriodRules.unpaidDaysIn(r.getStartDate(), r.getEndDate(), r.isHalfDay(),
                    run.getPeriodStart(), run.getPeriodEnd(), holidays), BigDecimal::add);

        List<String> warnings = new java.util.ArrayList<>();
        if (taxYear != run.getTaxYear())
            warnings.add("This run was labelled tax year " + run.getTaxYear() + " but its pay date falls in " + taxYear
                    + ". It was calculated under " + taxYear + ".");

        Map<UUID, MedicalSchemeRules.Members> members = medicalSchemeService.membersOn(tenantId, employees, run.getPayDate());
        // Taxable benefits in force on the last day of the period. They are not prorated for part months or unpaid leave.
        Map<UUID, BenefitRules.Monthly> benefits = benefitService.forRun(tenantId, run.getPeriodEnd());

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

        // Bonuses and back pay entered and dated up to the end of the period that no other run has taken. Taken again from scratch on a recalculation.
        Map<UUID, List<HrPayAdjustment>> payItems = payAdjustmentService.takeFor(tenantId, run.getId(), run.getPeriodEnd());
        java.util.Set<UUID> onRun = employees.stream().map(HrEmployee::getId).collect(java.util.stream.Collectors.toSet());
        int stranded = (int) payItems.entrySet().stream().filter(en -> !onRun.contains(en.getKey())).mapToLong(en -> en.getValue().size()).sum();
        if (stranded > 0)
            warnings.add(stranded + " bonus or back pay item(s) belong to people who are not on this run, so they were not paid. They stay open for a later run.");

        BigDecimal runGross = BigDecimal.ZERO, runPaye = BigDecimal.ZERO;
        BigDecimal runUif   = BigDecimal.ZERO, runSdl  = BigDecimal.ZERO;
        BigDecimal runNet   = BigDecimal.ZERO;

        boolean averaging = settingsRepo != null && settingsRepo.findById(tenantId.getValue()).map(HrPayrollSettings::averaging).orElse(false);
        if (averaging)
            warnings.add("PAYE on regular pay is worked out by the averaging method: the year to date is annualised and what was already paid is taken off. "
                    + "Bonuses, back pay and notice pay are taxed as annual payments on top.");

        for (HrEmployee emp : employees) {
            MedicalSchemeRules.Members m = members.get(emp.getId());
            if (m.assumed())
                warnings.add(emp.getFullName() + " (" + emp.getEmployeeNumber() + "): no medical scheme membership is on record, "
                        + "so the medical credit assumes the employee only. Record the membership to confirm it.");
            List<HrOvertimeClaim> claims = overtime.getOrDefault(emp.getId(), List.of());
            BigDecimal overtimePay = claims.stream().map(HrOvertimeClaim::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            // Pay for the period: the days employed less unpaid leave, and on a last pay the annual leave owing.
            String who = emp.getFullName() + " (" + emp.getEmployeeNumber() + ")";
            PayPeriodRules.Pay pay = PayPeriodRules.pay(emp.getGrossSalary(), run.getPeriodStart(), run.getPeriodEnd(), holidays,
                    emp.getStartDate(), "TERMINATED".equals(emp.getStatus()) ? emp.getEndDate() : null,
                    unpaidDays.getOrDefault(emp.getId(), BigDecimal.ZERO));
            boolean finalPay = PayPeriodRules.isFinal(emp.getStatus(), emp.getEndDate(), run.getPeriodEnd());
            BigDecimal payoutDays = BigDecimal.ZERO, payout = BigDecimal.ZERO;
            if (finalPay) {
                payoutDays = leaveService.annualLeaveOwing(tenantId, emp, emp.getEndDate());
                payout = PayPeriodRules.leavePayout(emp.getGrossSalary(), payoutDays);
                warnings.add(who + ": last pay (left " + emp.getEndDate() + ")"
                        + (payout.signum() > 0 ? ", including " + payoutDays + " day(s) of annual leave paid out at " + payout + "." : ", no annual leave owing.")
                        + " Any bonus must be entered under Pay items.");
                String notice = NoticeRules.summary(emp.getEndType(), emp.getGrossSalary(), emp.getStartDate(), emp.getNoticeGivenOn(), emp.getEndDate());
                boolean noticeEntered = payItems.getOrDefault(emp.getId(), List.of()).stream().anyMatch(i -> PayAdjustmentRules.NOTICE_PAY.equals(i.getKind()));
                BigDecimal noticeOwed = NoticeRules.owedPay(emp.getEndType(), emp.getGrossSalary(), emp.getStartDate(), emp.getNoticeGivenOn(), emp.getEndDate());
                warnings.add(who + ": " + (notice != null ? notice : "how employment ended was not recorded, so notice pay was not worked out; check it by hand.")
                        + (noticeEntered ? " Pay in place of notice is entered and included."
                        : noticeOwed.signum() > 0 ? " It is not in this run: enter it under Pay items if it is owed." : ""));
                BigDecimal advanceDays = leaveService.annualLeaveAdvance(tenantId, emp, emp.getEndDate());
                if (advanceDays != null && advanceDays.signum() > 0) {
                    boolean recovered = payItems.getOrDefault(emp.getId(), List.of()).stream().anyMatch(i -> PayAdjustmentRules.RECOVERY.equals(i.getKind()) && LeaveAdvanceRules.DESCRIPTION.equals(i.getDescription()));
                    warnings.add(who + ": " + advanceDays + " day(s) of annual leave were taken before they were earned (about "
                            + LeaveAdvanceRules.amount(emp.getGrossSalary(), advanceDays) + ")."
                            + (recovered ? " A recovery is entered and is taken from net pay if it fits."
                            : " Nothing is taken off pay unless the employee's written agreement is recorded under Pay items."));
                }
            }
            if (pay.partPeriod() && !finalPay)
                warnings.add(who + ": paid " + pay.daysPaid() + " of " + pay.workingDays() + " working days"
                        + (pay.proration().signum() > 0 ? " (joined or left part way through)" : "") + ".");
            if (pay.unpaidDays().signum() > 0)
                warnings.add(who + ": " + pay.unpaidDays() + " day(s) of unpaid leave deducted (" + pay.unpaidAmount() + ").");
            BigDecimal travel = PayPeriodRules.share(emp.getTravelAllowance(), pay);
            BenefitRules.Monthly benefit = benefits.getOrDefault(emp.getId(), BenefitRules.Monthly.none());
            if (!benefit.isEmpty())
                warnings.add(who + ": taxable benefits of " + benefit.medical().add(benefit.retirement()).add(benefit.other())
                        + " a month (not paid in cash) are included in the income PAYE is worked out on.");
            List<HrPayAdjustment> items = payItems.getOrDefault(emp.getId(), List.of());
            BigDecimal bonus = PayAdjustmentService.total(items, PayAdjustmentRules.BONUS);
            BigDecimal backPay = PayAdjustmentService.total(items, PayAdjustmentRules.BACK_PAY);
            BigDecimal noticePay = PayAdjustmentService.total(items, PayAdjustmentRules.NOTICE_PAY);
            java.util.List<String> lumps = new ArrayList<>();
            if (bonus.signum() > 0) lumps.add("bonus " + bonus);
            if (backPay.signum() > 0) lumps.add("back pay " + backPay);
            if (noticePay.signum() > 0) lumps.add("pay in place of notice " + noticePay);
            if (!lumps.isEmpty())
                warnings.add(who + ": " + String.join(" and ", lumps)
                        + " included. Taxed by the annual-payment method: the extra tax the year bears once it is added to the annualised month.");
            PayrollEngine.Prior prior = null;
            if (averaging) {
                prior = new PayrollEngine.Prior(payslipRepo.sumRegularTaxable(emp.getId(), taxYear, run.getPayDate()),
                        payslipRepo.sumRegularPaye(emp.getId(), taxYear, run.getPayDate()),
                        PayeAveragingRules.monthsElapsed(rules.effectiveFrom(), emp.getStartDate(), run.getPayDate()));
            }
            PayrollEngine.Calc calc = engine.calculate(emp, rules, annualPayroll, m.count(), overtimePay,
                    new PayrollEngine.Period(pay.paid(), travel, emp.getPensionContribution(), payout,
                            benefit.medical(), benefit.retirement(), benefit.other(), bonus, backPay, noticePay), prior);
            PayrollEngine.PayrollResult result = calc.result();
            if (averaging && calc.regularPaye().compareTo(calc.periodicPaye()) != 0)
                warnings.add(who + ": PAYE on regular pay is " + calc.regularPaye() + " by the averaging method (the periodic method would give "
                        + calc.periodicPaye() + ").");

            HrPayslip slip = HrPayslip.create(tenantId, run.getId(), emp.getId(),
                    result.grossSalary(), result.travelAllowance(),
                    result.medicalAid(), result.pension());
            slip.setOvertimeAmount(overtimePay);
            slip.setBenefits(benefit.medical(), benefit.retirement(), benefit.other());
            slip.setBonusAndBackPay(bonus, backPay);
            slip.setNoticePay(noticePay);
            slip.setRegular(calc.regularTaxable(), calc.regularPaye());
            slip.setPeriodPay(pay.workingDays(), pay.daysPaid(), pay.proration(), pay.unpaidDays(), pay.unpaidAmount());
            if (finalPay) { slip.setLeavePayout(payoutDays, payout); slip.markFinalPay(); }

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

            // Overpayments are recovered from net pay, after tax, whole or not at all.
            List<HrPayAdjustment> recoveries = items.stream().filter(a -> PayAdjustmentRules.RECOVERY.equals(a.getKind()))
                    .sorted(java.util.Comparator.comparing(HrPayAdjustment::getCreatedAt)).toList();
            List<HrPayAdjustment> taken = new ArrayList<>(items.stream().filter(a -> !PayAdjustmentRules.RECOVERY.equals(a.getKind())).toList());
            BigDecimal recovered = BigDecimal.ZERO;
            List<Boolean> fits = PayAdjustmentRules.affordable(recoveries.stream().map(HrPayAdjustment::getAmount).toList(), slip.getNetPay());
            for (int i = 0; i < recoveries.size(); i++) {
                if (fits.get(i)) { taken.add(recoveries.get(i)); recovered = recovered.add(recoveries.get(i).getAmount()); }
                else warnings.add(who + ": the recovery of " + recoveries.get(i).getAmount() + " (" + recoveries.get(i).getDescription()
                        + ") was not taken because net pay is not enough. It stays open for the next run.");
            }
            if (recovered.signum() > 0) {
                slip.recover(recovered);
                warnings.add(who + ": " + recovered + " recovered from net pay (overpayment, after tax).");
            }

            payslipRepo.save(slip);
            overtimeService.markTaken(claims, run.getId());
            payAdjustmentService.markTaken(taken, run.getId());

            runGross = runGross.add(result.totalEarnings());
            runPaye  = runPaye.add(result.payeAmount());
            runUif   = runUif.add(result.uifEmployee()).add(result.uifEmployer());
            runSdl   = runSdl.add(result.sdlAmount());
            runNet   = runNet.add(slip.getNetPay());
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
        if (!slips.isEmpty()) {
            Map<UUID, String> status = new java.util.HashMap<>();
            payRunRepo.findAllById(slips.stream().map(HrPayslip::getPayRunId).distinct().toList())
                    .forEach(r -> status.put(r.getId(), r.getStatus()));
            // A cancelled run was never paid, so its payslips are not shown to anyone.
            slips = slips.stream()
                    .filter(sl -> !PayRunWorkflowRules.CANCELLED.equals(status.get(sl.getPayRunId())))
                    .filter(sl -> !issuedOnly || PayRunWorkflowRules.isIssued(status.get(sl.getPayRunId())))
                    .toList();
        }
        return slips.stream().map(sl -> toPayslipResponse(sl, tenantId)).toList();
    }

    // ── EMP201 ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Emp201Response> getEmp201s(TenantId tenantId) {
        List<HrEmp201> all = emp201Repo.findAllByTenant(tenantId);
        Set<UUID> issued = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        for (HrEmp201 e : all) if (e.getPayRunId() != null) ids.add(e.getPayRunId());
        if (!ids.isEmpty()) for (HrPayRun r : payRunRepo.findAllById(ids)) if (PayRunWorkflowRules.isIssued(r.getStatus())) issued.add(r.getId());
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg"));
        return all.stream().map(e -> toEmp201Response(e, today, e.getPayRunId() == null || issued.contains(e.getPayRunId()))).toList();
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
                s.getCreatedAt(), s.getDaysInPeriod(), s.getDaysPaid(), s.getProrationAmount(), s.getUnpaidLeaveDays(), s.getUnpaidLeaveAmount(),
                s.getLeavePayoutDays(), s.getLeavePayoutAmount(), s.isFinalPay(),
                s.getEmployerMedical(), s.getEmployerRetirement(), s.getOtherFringe(), s.getBackPayAmount(), s.getNoticePayAmount());
    }

    private Emp201Response toEmp201Response(HrEmp201 e, LocalDate today, boolean runIssued) {
        return new Emp201Response(e.getId(), e.getPayRunId(),
                e.getPeriodStart(), e.getPeriodEnd(), e.getDueDate(),
                e.getTotalPaye(), e.getTotalUif(), e.getTotalSdl(),
                e.getTotalPayable(), e.getStatus(), e.getSubmittedAt(), e.getCreatedAt(),
                e.getPaymentRef(), e.getPaidOn(), e.getPaidAmount(),
                Emp201Rules.overdue(e.getStatus(), e.getDueDate(), today), Emp201Rules.late(e.getPaidOn(), e.getDueDate()), runIssued);
    }
}