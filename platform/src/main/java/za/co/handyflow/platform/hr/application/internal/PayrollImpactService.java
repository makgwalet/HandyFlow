package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.model.PayrollImpactRules.Facts;
import za.co.handyflow.platform.hr.domain.model.PayrollImpactRules.Verdict;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.PayrollImpactResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.SarsPayeCalculator;
import za.co.handyflow.platform.shared.SarsPayrollResult;
import za.co.handyflow.platform.shared.StatutoryYearRules;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Recalculates pay runs already on record under the approved statutory rules and reports how they differ.
 * <p>
 * Read-only: it never changes a payslip, a run or an EMP201. It works from what each payslip recorded (salary,
 * allowance, pension, medical aid) and the medical scheme membership in force on the pay date, not from today's
 * employee record, except for the date of birth, which does not change.
 */
@Service
@RequiredArgsConstructor
public class PayrollImpactService {

    private static final int MAX_RUNS = 200;

    private final HrPayRunRepository payRuns;
    private final HrPayslipRepository payslips;
    private final HrEmployeeRepository employees;
    private final HrEmp201Repository emp201s;
    private final HrMedicalSchemeMembershipRepository memberships;
    private final StatutoryRulesService rulesService;

    /** All runs (newest first, at most 200), or one run with a line per payslip. */
    @Transactional(readOnly = true)
    public PayrollImpactResponse report(TenantId tenantId, UUID runId) {
        List<HrPayRun> runs;
        if (runId != null) {
            runs = List.of(payRuns.findByTenantAndId(tenantId, runId)
                    .orElseThrow(() -> new ResourceNotFoundException("PayRun", runId.toString())));
        } else {
            runs = payRuns.findAllByTenant(tenantId, PageRequest.of(0, MAX_RUNS)).getContent();
        }

        Map<Integer, Optional<StatutoryYearRules>> rulesByYear = new HashMap<>();
        List<PayrollImpactResponse.Run> out = new ArrayList<>();
        for (HrPayRun run : runs) {
            List<HrPayslip> slips = payslips.findByPayRun(run.getId());
            if (slips.isEmpty()) continue;   // a draft that was never processed has nothing to compare
            out.add(impactOf(tenantId, run, slips, rulesByYear, runId != null));
        }
        int withDifferences = (int) out.stream()
                .filter(r -> !Verdict.NO_DIFFERENCE.name().equals(r.verdict())).count();
        return new PayrollImpactResponse(Instant.now(), out, withDifferences, NOTES);
    }

    private PayrollImpactResponse.Run impactOf(TenantId tenantId, HrPayRun run, List<HrPayslip> slips,
                                               Map<Integer, Optional<StatutoryYearRules>> rulesByYear, boolean withLines) {
        int correctYear = StatutoryYearRules.taxYearOf(run.getPayDate());
        Optional<StatutoryYearRules> rules = rulesByYear.computeIfAbsent(correctYear, rulesService::find);

        Map<UUID, HrEmployee> staff = new HashMap<>();
        employees.findAllById(slips.stream().map(HrPayslip::getEmployeeId).distinct().toList())
                .stream().filter(e -> tenantId.getValue().equals(e.getTenantId())).forEach(e -> staff.put(e.getId(), e));
        Map<UUID, List<MedicalSchemeRules.Cover>> covers = new HashMap<>();
        memberships.forEmployees(tenantId, staff.keySet())
                .forEach(m -> covers.computeIfAbsent(m.getEmployeeId(), k -> new ArrayList<>()).add(m.toCover()));

        // SDL looks at the whole payroll, so use this run's gross, on the same basis the run was processed on.
        BigDecimal annualPayroll = slips.stream().map(HrPayslip::getGrossSalary).reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(12));

        List<PayrollImpactResponse.Line> lines = new ArrayList<>();
        List<Verdict> verdicts = new ArrayList<>();
        BigDecimal recordedPaye = BigDecimal.ZERO, recalculatedPaye = BigDecimal.ZERO;
        for (HrPayslip s : slips) {
            HrEmployee emp = staff.get(s.getEmployeeId());
            boolean ignored = positive(s.getOvertimeAmount()) || positive(s.getBonusAmount()) || positive(s.getBackPayAmount());
            recordedPaye = recordedPaye.add(nvl(s.getPayeAmount()));

            if (rules.isEmpty() || emp == null) {
                Facts f = new Facts(rules.isEmpty(), emp == null, ignored, false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                verdicts.add(PayrollImpactRules.classify(f));
                recalculatedPaye = recalculatedPaye.add(nvl(s.getPayeAmount()));   // nothing to compare: counts as unchanged in the total
                lines.add(line(s, emp, PayrollImpactRules.classify(f), null, null, null, f, run.getTaxYear(), correctYear));
                continue;
            }

            MedicalSchemeRules.Members members = MedicalSchemeRules.membersOn(
                    covers.getOrDefault(emp.getId(), List.of()), run.getPayDate(), s.getMedicalAid());
            SarsPayrollResult again = SarsPayeCalculator.calculate(new SarsPayeCalculator.Inputs(
                    s.getGrossSalary(), s.getTravelAllowance(), s.getPension(), s.getMedicalAid(),
                    emp.getDateOfBirth(), members.count(), annualPayroll,
                    s.getEmployerMedical(), s.getEmployerRetirement(), s.getOtherFringe()), rules.get());
            Facts f = new Facts(false, false, ignored, members.assumed(),
                    again.payeAmount().subtract(nvl(s.getPayeAmount())),
                    again.uifEmployee().subtract(nvl(s.getUifEmployee())),
                    again.sdlAmount().subtract(nvl(s.getSdlAmount())));
            Verdict v = PayrollImpactRules.classify(f);
            verdicts.add(v);
            recalculatedPaye = recalculatedPaye.add(again.payeAmount());
            lines.add(line(s, emp, v, again.payeAmount(), members.count(), members.assumed(), f, run.getTaxYear(), correctYear));
        }

        Verdict runVerdict = PayrollImpactRules.runVerdict(verdicts);
        String emp201Status = emp201s.forPayRun(tenantId, run.getId()).stream().findFirst().map(HrEmp201::getStatus).orElse(null);
        return new PayrollImpactResponse.Run(run.getId(), run.getPayRunNumber(), run.getPeriodStart(), run.getPeriodEnd(), run.getPayDate(),
                run.getTaxYear(), correctYear, run.getStatus(), emp201Status, slips.size(),
                count(verdicts, Verdict.NO_DIFFERENCE), count(verdicts, Verdict.POTENTIAL_DIFFERENCE),
                count(verdicts, Verdict.CONFIRMED_DIFFERENCE), count(verdicts, Verdict.REVIEW_REQUIRED),
                recordedPaye, recalculatedPaye, recalculatedPaye.subtract(recordedPaye), runVerdict.name(),
                action(runVerdict, run, emp201Status), withLines ? lines : null);
    }

    private PayrollImpactResponse.Line line(HrPayslip s, HrEmployee emp, Verdict v, BigDecimal recalculated, Integer members,
                                            Boolean assumed, Facts f, int recordedYear, int correctYear) {
        return new PayrollImpactResponse.Line(s.getId(), s.getEmployeeId(),
                emp != null ? emp.getFullName() : "Unknown", emp != null ? emp.getEmployeeNumber() : "—",
                v.name(), nvl(s.getPayeAmount()), recalculated,
                recalculated == null ? null : f.payeDifference(), recalculated == null ? null : f.uifDifference(),
                recalculated == null ? null : f.sdlDifference(), members, Boolean.TRUE.equals(assumed),
                PayrollImpactRules.reasons(f, recordedYear, correctYear));
    }

    static String action(Verdict v, HrPayRun run, String emp201Status) {
        if (v == Verdict.NO_DIFFERENCE) return "No action needed.";
        if ("DRAFT".equals(run.getStatus()))
            return "This run is still a draft. Process it again once the rules are in place; nothing has been issued.";
        if ("PREPARED".equals(run.getStatus()) || "APPROVED".equals(run.getStatus()))
            return "This run has not been finalised, so employees have not seen these payslips. Send it back and calculate it again under the approved rules.";
        String emp201 = emp201Status == null ? "" : " and a review of the EMP201 (status " + emp201Status + ")";
        return "This run is already processed. A correction through an approved adjustment, a payslip reissue" + emp201
                + " may be needed. Nothing has been changed.";
    }

    private static final List<String> NOTES = List.of(
            "Nothing was changed. This report only recalculates and compares.",
            "Each payslip is recalculated from what it recorded, using the approved rules of the tax year its pay date falls in.",
            "Whether a run was paid, exported or reconciled is not recorded yet, so only its status and EMP201 status are shown.",
            "Overtime and bonus on a payslip were never taxed by the old calculation; those payslips are marked for review.",
            "Where no medical scheme membership is on record, the credit is assumed for the employee only when a medical aid contribution was deducted. Those differences are 'potential' until the membership is recorded.");

    private static int count(List<Verdict> all, Verdict v) { return (int) all.stream().filter(x -> x == v).count(); }
    private static boolean positive(BigDecimal d) { return d != null && d.signum() > 0; }
    private static BigDecimal nvl(BigDecimal d) { return d == null ? BigDecimal.ZERO : d; }
}
