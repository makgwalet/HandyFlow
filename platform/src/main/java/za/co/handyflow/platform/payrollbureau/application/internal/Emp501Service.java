package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.payrollbureau.domain.model.*;
import za.co.handyflow.platform.payrollbureau.domain.repository.*;
import za.co.handyflow.platform.payrollbureau.domain.rules.CsvRules;
import za.co.handyflow.platform.payrollbureau.domain.rules.PayComponentRules;
import za.co.handyflow.platform.payrollbureau.domain.rules.TaxCertificateRules;
import za.co.handyflow.platform.payrollbureau.domain.rules.TaxCertificateRules.*;
import za.co.handyflow.platform.payrollbureau.dto.PayBankFileDtos.GeneratedFile;
import za.co.handyflow.platform.payrollbureau.dto.PayEmp501Dtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * The EMP501 reconciliation and employee tax-certificate figures (phase 4). Worked out on request from finalised runs by pay
 * date and the client's EMP201 returns; nothing is stored. The period is the interim (March to August) or annual (March to
 * February) reconciliation of a tax year, given as the year it ends in. This produces figures and checks for guided filing;
 * it does not produce SARS's certificate CSV.
 */
@Service
@RequiredArgsConstructor
public class Emp501Service {

    public static final String INTERIM = "INTERIM", ANNUAL = "ANNUAL";

    private final PayClientRepository clientRepo;
    private final PayEmployeeRepository employeeRepo;
    private final PayRunRepository runRepo;
    private final PayslipRepository payslipRepo;
    private final PayslipLineRepository lineRepo;
    private final PayEmp201ReturnRepository returnRepo;

    private static BigDecimal z(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static class Acc {
        List<Amount> income = new ArrayList<>();
        BigDecimal pension = BigDecimal.ZERO, medical = BigDecimal.ZERO, paye = BigDecimal.ZERO, uif = BigDecimal.ZERO, sdl = BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public Emp501Response build(TenantId tenantId, UUID clientId, String period, int taxYear) {
        UUID tenant = tenantId.getValue();
        clientRepo.findActiveById(tenant, clientId).orElseThrow(() -> new ResourceNotFoundException("PayClient", clientId.toString()));
        if (!INTERIM.equals(period) && !ANNUAL.equals(period)) {
            throw new HandyFlowException("Period must be INTERIM or ANNUAL.", HttpStatus.BAD_REQUEST, "INVALID_PERIOD");
        }
        LocalDate from = LocalDate.of(taxYear - 1, 3, 1);
        LocalDate to = INTERIM.equals(period) ? LocalDate.of(taxYear - 1, 8, 31) : LocalDate.of(taxYear, 2, 1).plusMonths(1).minusDays(1);

        List<PayRun> runs = runRepo.findFinalisedByPayDate(tenant, clientId, from, to);
        if (runs.isEmpty()) {
            throw new HandyFlowException("There are no finalised pay runs for this client in that period.", HttpStatus.BAD_REQUEST, "NO_RUNS_IN_PERIOD");
        }
        Set<String> expectedMonths = new TreeSet<>();
        Map<UUID, Acc> byEmployee = new LinkedHashMap<>();
        for (PayRun run : runs) {
            expectedMonths.add(YearMonth.from(run.getPayDate()).toString());
            for (Payslip p : payslipRepo.findByPayRun(tenant, run.getId())) {
                Acc a = byEmployee.computeIfAbsent(p.getPayEmployeeId(), k -> new Acc());
                a.income.add(new Amount("3601", p.getGrossSalary()));
                if (z(p.getTravelAllowance()).signum() > 0) a.income.add(new Amount("3701", p.getTravelAllowance()));
                for (PayslipLine l : lineRepo.findByPayslip(tenant, p.getId())) {
                    if (PayComponentRules.DEDUCTION.equals(l.getRole())) continue;
                    String code = l.getSarsCode() != null ? l.getSarsCode()
                            : PayComponentRules.NON_TAXABLE_EARNING.equals(l.getRole()) ? "3602" : "3601";
                    a.income.add(new Amount(code, l.getAmount()));
                }
                a.pension = a.pension.add(z(p.getPension()));
                a.medical = a.medical.add(z(p.getMedicalAid()));
                a.paye = a.paye.add(z(p.getPayeAmount()));
                a.uif = a.uif.add(z(p.getUifEmployee())).add(z(p.getUifEmployer()));
                a.sdl = a.sdl.add(z(p.getSdlAmount()));
            }
        }

        List<Certificate> certs = new ArrayList<>();
        List<CertificateResponse> certResponses = new ArrayList<>();
        for (var e : byEmployee.entrySet()) {
            PayEmployee emp = employeeRepo.findActiveById(tenant, e.getKey()).orElse(null);
            Acc a = e.getValue();
            String name = emp == null ? "(removed employee)" : emp.getFullName();
            Certificate c = TaxCertificateRules.build(new Input(name, emp == null ? null : emp.getIdNumber(),
                    emp == null ? null : emp.getTaxNumber(), a.income, a.pension, a.medical, a.paye, a.uif, a.sdl));
            certs.add(c);
            certResponses.add(toResponse(e.getKey(), c));
        }

        List<Monthly> monthly = returnRepo.findInRange(tenant, clientId, from, to).stream()
                .map(r -> new Monthly(YearMonth.from(r.getTaxMonth()).toString(), r.getStatus(), r.getPaye(),
                        r.getUifEmployee().add(r.getUifEmployer()), r.getSdl(), r.getEtiUsed(), r.getEtiCarriedForward()))
                .toList();
        Reconciliation rec = TaxCertificateRules.reconcile(monthly, new ArrayList<>(expectedMonths), certs);

        boolean blocked = certs.stream().anyMatch(Certificate::blocked)
                || rec.findings().stream().anyMatch(f -> TaxCertificateRules.BLOCK.equals(f.severity()));
        return new Emp501Response(clientId, period, taxYear, from, to, certs.size(), rec.balanced(), blocked,
                rec.differences().stream().map(d -> new DifferenceResponse(d.what(), d.emp201(), d.certificates(), d.difference())).toList(),
                rec.etiUsed(), rec.etiUnusedAtEnd(),
                rec.findings().stream().map(f -> new FindingResponse(f.severity(), f.code(), f.message())).toList(), certResponses);
    }

    private static CertificateResponse toResponse(UUID employeeId, Certificate c) {
        return new CertificateResponse(employeeId, c.employeeName(), c.type(),
                c.income().stream().map(l -> new LineResponse(l.code(), l.amount())).toList(),
                c.deductions().stream().map(l -> new LineResponse(l.code(), l.amount())).toList(),
                c.nonTaxable3696(), c.grossTaxable3699(), c.paye4102(), c.uif4141(), c.sdl4142(), c.total4149(), c.blocked(),
                c.findings().stream().map(f -> new FindingResponse(f.severity(), f.code(), f.message())).toList());
    }

    /** A CSV working paper of the certificate figures for the bureau's own review. It is not SARS's submission file. */
    @Transactional(readOnly = true)
    public GeneratedFile workingPaper(TenantId tenantId, UUID clientId, String period, int taxYear) {
        Emp501Response r = build(tenantId, clientId, period, taxYear);
        StringBuilder sb = new StringBuilder("Employee,Certificate,Kind,Code,Amount\r\n");
        for (CertificateResponse c : r.certificates()) {
            for (LineResponse l : c.income()) sb.append(CsvRules.row(List.of(c.employeeName(), c.type(), "Income", l.code(), CsvRules.money(l.amount())))).append("\r\n");
            for (LineResponse l : c.deductions()) sb.append(CsvRules.row(List.of(c.employeeName(), c.type(), "Deduction", l.code(), CsvRules.money(l.amount())))).append("\r\n");
            sb.append(CsvRules.row(List.of(c.employeeName(), c.type(), "Total", "3696", CsvRules.money(c.nonTaxable3696())))).append("\r\n");
            sb.append(CsvRules.row(List.of(c.employeeName(), c.type(), "Total", "3699", CsvRules.money(c.grossTaxable3699())))).append("\r\n");
            sb.append(CsvRules.row(List.of(c.employeeName(), c.type(), "Tax", "4102", CsvRules.money(c.paye4102())))).append("\r\n");
            sb.append(CsvRules.row(List.of(c.employeeName(), c.type(), "Tax", "4141", CsvRules.money(c.uif4141())))).append("\r\n");
            sb.append(CsvRules.row(List.of(c.employeeName(), c.type(), "Tax", "4142", CsvRules.money(c.sdl4142())))).append("\r\n");
            sb.append(CsvRules.row(List.of(c.employeeName(), c.type(), "Tax", "4149", CsvRules.money(c.total4149())))).append("\r\n");
        }
        return new GeneratedFile("tax-certificate-working-paper-" + period.toLowerCase() + "-" + taxYear + ".csv",
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
