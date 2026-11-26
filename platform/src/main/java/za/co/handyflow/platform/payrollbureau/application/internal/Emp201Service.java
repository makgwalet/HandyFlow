package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.payrollbureau.domain.model.*;
import za.co.handyflow.platform.payrollbureau.domain.repository.*;
import za.co.handyflow.platform.payrollbureau.domain.rules.EtiFactsRules;
import za.co.handyflow.platform.payrollbureau.domain.rules.EtiRules;
import za.co.handyflow.platform.payrollbureau.dto.PayEmp201Dtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * The EMP201 monthly figures (phase 4). A tax month is made of the client's FINALISED runs whose pay date falls in it;
 * reversed runs are left out. ETI is worked out per employee on their pay across the month and taken off the PAYE only
 * when staff have marked the client as allowed to claim. The figures are for keying into SARS eFiling; nothing is submitted.
 */
@Slf4j
@Service("payBureauEmp201Service")
@RequiredArgsConstructor
public class Emp201Service {

    private final PayClientRepository clientRepo;
    private final PayEmployeeRepository employeeRepo;
    private final PayRunRepository runRepo;
    private final PayslipRepository payslipRepo;
    private final PayEmp201ReturnRepository returnRepo;
    private final PayEmp201EtiLineRepository lineRepo;
    private final PayDeadlineEngine deadlineEngine;

    private static BigDecimal z(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    private PayClient client(TenantId tenantId, UUID clientId) {
        return clientRepo.findActiveById(tenantId.getValue(), clientId)
                .orElseThrow(() -> new ResourceNotFoundException("PayClient", clientId.toString()));
    }

    /** The SARS payment date: the 7th of the month after, or the business day before when that is a weekend or holiday. */
    public LocalDate dueDate(YearMonth month) {
        return deadlineEngine.adjustForBusinessDay(month.plusMonths(1).atDay(7));
    }

    private static class Totals {
        BigDecimal remuneration = BigDecimal.ZERO, paye = BigDecimal.ZERO, uifEmployee = BigDecimal.ZERO,
                uifEmployer = BigDecimal.ZERO, sdl = BigDecimal.ZERO;
    }

    @Transactional
    public Emp201Response prepare(TenantId tenantId, UUID clientId, YearMonth month) {
        UUID tenant = tenantId.getValue();
        PayClient client = client(tenantId, clientId);
        LocalDate first = month.atDay(1);
        PayEmp201Return ret = returnRepo.findByMonth(tenant, clientId, first).orElse(null);
        if (ret != null && ret.isFiled()) {
            throw new HandyFlowException("The EMP201 for " + month + " is already marked as filed. Its figures are the record of what was sent to SARS.",
                    HttpStatus.CONFLICT, "EMP201_FILED");
        }
        List<PayRun> runs = runRepo.findFinalisedByPayDate(tenant, clientId, first, month.atEndOfMonth());
        if (runs.isEmpty()) {
            throw new HandyFlowException("There are no finalised pay runs with a pay date in " + month + " for this client.",
                    HttpStatus.BAD_REQUEST, "NO_RUNS_IN_MONTH");
        }
        Map<UUID, Totals> byEmployee = new LinkedHashMap<>();
        for (PayRun run : runs) {
            for (Payslip p : payslipRepo.findByPayRun(tenant, run.getId())) {
                Totals t = byEmployee.computeIfAbsent(p.getPayEmployeeId(), k -> new Totals());
                t.remuneration = t.remuneration.add(z(p.getTotalEarnings()));
                t.paye = t.paye.add(z(p.getPayeAmount()));
                t.uifEmployee = t.uifEmployee.add(z(p.getUifEmployee()));
                t.uifEmployer = t.uifEmployer.add(z(p.getUifEmployer()));
                t.sdl = t.sdl.add(z(p.getSdlAmount()));
            }
        }
        Totals all = new Totals();
        byEmployee.values().forEach(t -> {
            all.remuneration = all.remuneration.add(t.remuneration); all.paye = all.paye.add(t.paye);
            all.uifEmployee = all.uifEmployee.add(t.uifEmployee); all.uifEmployer = all.uifEmployer.add(t.uifEmployer);
            all.sdl = all.sdl.add(t.sdl);
        });

        if (ret == null) ret = PayEmp201Return.create(tenant, clientId, first);
        else lineRepo.deleteByReturn(tenant, ret.getId());
        // save first so the lines have a return id (a new return is inserted here)
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        ret.fill(0, zero, zero, zero, zero, zero, zero, zero, zero, zero, client.isEtiClaimable(), zero, dueDate(month), null, null);
        ret = returnRepo.save(ret);

        List<PayEmp201EtiLine> lines = new ArrayList<>();
        BigDecimal etiTotal = BigDecimal.ZERO;
        for (var e : byEmployee.entrySet()) {
            PayEmployee emp = employeeRepo.findActiveById(tenant, e.getKey()).orElse(null);
            if (emp == null) continue;
            LocalDate dob = emp.getDateOfBirth() != null ? emp.getDateOfBirth()
                    : EtiFactsRules.dateOfBirth(emp.getIdNumber(), month.atEndOfMonth());
            int claimed = emp.getEtiMonthsPrior() + (int) lineRepo.monthsClaimedBefore(tenant, emp.getId(), first);
            var facts = new EtiRules.Facts(EtiFactsRules.idKind(emp.getIdNumber(), emp.getEtiIdKind()), dob, emp.getStartDate(),
                    emp.isDomesticWorker(), emp.isConnectedPerson(), claimed, !emp.isBelowMinimumWage(),
                    e.getValue().remuneration, null);
            var r = EtiRules.calculate(facts, month, EtiRules.FROM_2025_04_01);
            etiTotal = etiTotal.add(r.incentive());
            lines.add(PayEmp201EtiLine.create(tenant, ret.getId(), emp.getId(), r.eligible(), r.tier(),
                    money(e.getValue().remuneration), r.incentive(), String.join(",", r.reasons()), r.ruleVersion()));
        }
        lineRepo.saveAll(lines);

        BigDecimal broughtForward = BigDecimal.ZERO;
        if (month.getMonthValue() != 3) { // a new tax year starts in March; nothing is carried across it
            PayEmp201Return prev = returnRepo.findByMonth(tenant, clientId, first.minusMonths(1)).orElse(null);
            if (prev != null && prev.isEtiClaimed()) broughtForward = prev.getEtiCarriedForward();
        }
        BigDecimal used = BigDecimal.ZERO, carried = BigDecimal.ZERO;
        if (client.isEtiClaimable()) {
            var applied = EtiRules.applyToPaye(etiTotal, broughtForward, all.paye);
            used = applied.used(); carried = applied.carriedForward();
        }
        BigDecimal payable = all.paye.subtract(used).add(all.uifEmployee).add(all.uifEmployer).add(all.sdl);
        String runNumbers = String.join(", ", runs.stream().map(PayRun::getPayRunNumber).toList());
        ret.fill(byEmployee.size(), money(all.remuneration), money(all.paye), money(all.uifEmployee), money(all.uifEmployer),
                money(all.sdl), money(etiTotal), money(broughtForward), money(used), money(carried), client.isEtiClaimable(),
                money(payable), dueDate(month), runNumbers.length() > 500 ? runNumbers.substring(0, 500) : runNumbers,
                TenantContext.getCurrentUserId());
        ret = returnRepo.save(ret);
        return toResponse(tenant, ret, lines);
    }

    @Transactional
    public Emp201Response markFiled(TenantId tenantId, UUID clientId, YearMonth month, String reference) {
        UUID tenant = tenantId.getValue();
        client(tenantId, clientId);
        PayEmp201Return ret = returnRepo.findByMonth(tenant, clientId, month.atDay(1))
                .orElseThrow(() -> new HandyFlowException("Prepare the EMP201 for " + month + " before marking it as filed.",
                        HttpStatus.BAD_REQUEST, "EMP201_NOT_PREPARED"));
        if (ret.isFiled()) return toResponse(tenant, ret, lineRepo.findByReturn(tenant, ret.getId()));
        ret.markFiled(reference == null || reference.isBlank() ? null : reference.trim(), TenantContext.getCurrentUserId());
        ret = returnRepo.save(ret);
        return toResponse(tenant, ret, lineRepo.findByReturn(tenant, ret.getId()));
    }

    @Transactional(readOnly = true)
    public Emp201Response get(TenantId tenantId, UUID clientId, YearMonth month) {
        client(tenantId, clientId);
        PayEmp201Return ret = returnRepo.findByMonth(tenantId.getValue(), clientId, month.atDay(1))
                .orElseThrow(() -> new ResourceNotFoundException("Emp201Return", month.toString()));
        return toResponse(tenantId.getValue(), ret, lineRepo.findByReturn(tenantId.getValue(), ret.getId()));
    }

    @Transactional(readOnly = true)
    public List<Emp201Response> list(TenantId tenantId, UUID clientId, int taxYearStartingMarch) {
        client(tenantId, clientId);
        LocalDate from = LocalDate.of(taxYearStartingMarch - 1, 3, 1), to = LocalDate.of(taxYearStartingMarch, 2, 1);
        return returnRepo.findInRange(tenantId.getValue(), clientId, from, to).stream()
                .map(r -> toResponse(tenantId.getValue(), r, List.of())).toList();
    }

    @Transactional
    public void setClientEtiClaim(TenantId tenantId, UUID clientId, boolean claimable, String note) {
        PayClient c = client(tenantId, clientId);
        if (!claimable && (note == null || note.isBlank()) && c.isEtiClaimable()) {
            throw new HandyFlowException("Say why ETI is being switched off for this client.", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        }
        c.setEtiClaimable(claimable, note, TenantContext.getCurrentUserId());
        clientRepo.save(c);
    }

    @Transactional
    public void setEmployeeEti(TenantId tenantId, UUID clientId, UUID employeeId, EmployeeEtiRequest req) {
        PayEmployee e = employeeRepo.findActiveById(tenantId.getValue(), employeeId)
                .filter(x -> x.getPayClientId().equals(clientId))
                .orElseThrow(() -> new ResourceNotFoundException("PayEmployee", employeeId.toString()));
        String kind = req.idKind() == null || req.idKind().isBlank() ? null : req.idKind().trim().toUpperCase();
        if (kind != null && !List.of("SA_ID", "ASYLUM", "REFUGEE", "OTHER").contains(kind)) {
            throw new HandyFlowException("ID type must be SA_ID, ASYLUM, REFUGEE or OTHER.", HttpStatus.BAD_REQUEST, "INVALID_ID_KIND");
        }
        int months = req.monthsPrior() == null ? 0 : req.monthsPrior();
        if (months < 0 || months > 24) {
            throw new HandyFlowException("Months already claimed must be between 0 and 24.", HttpStatus.BAD_REQUEST, "INVALID_MONTHS");
        }
        e.setEtiFacts(kind, req.domesticWorker(), req.connectedPerson(), req.belowMinimumWage(), months);
        employeeRepo.save(e);
    }

    private Emp201Response toResponse(UUID tenant, PayEmp201Return r, List<PayEmp201EtiLine> lines) {
        List<EtiLineResponse> out = lines.stream().map(l -> {
            String name = employeeRepo.findActiveById(tenant, l.getPayEmployeeId()).map(PayEmployee::getFullName).orElse("(removed)");
            List<String> reasons = l.getReasons() == null || l.getReasons().isBlank() ? List.of() : List.of(l.getReasons().split(","));
            return new EtiLineResponse(l.getPayEmployeeId(), name, l.isEligible(), l.getTier(), l.getRemuneration(), l.getIncentive(), reasons);
        }).toList();
        return new Emp201Response(r.getId(), r.getPayClientId(), r.getTaxMonth(), r.getStatus(), r.getEmployeeCount(),
                r.getTotalRemuneration(), r.getPaye(), r.getUifEmployee(), r.getUifEmployer(), r.getSdl(),
                r.getEtiCalculated(), r.getEtiBroughtForward(), r.getEtiUsed(), r.getEtiCarriedForward(), r.isEtiClaimed(),
                r.getTotalPayable(), r.getDueDate(), r.getRunNumbers(), r.getPreparedAt(), r.getFiledAt(), r.getFilingReference(), out);
    }
}
