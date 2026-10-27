package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.model.TaxCertificateRules.Totals;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.TaxCertificateDtos;
import za.co.handyflow.platform.hr.dto.TaxCertificateDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.StatutoryYearRules;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tax certificates and the reconciliation. Built only from payslips of finalised pay runs, so what is certified is what was
 * locked. Nothing is issued while anything is wrong with the runs, the EMP201 returns, the employer details or an employee's
 * details. An issued certificate is never edited: a corrected one replaces it, with a new number.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxCertificateService {

    private final HrPayRunRepository payRunRepo;
    private final HrPayslipRepository payslipRepo;
    private final HrEmp201Repository emp201Repo;
    private final HrEmployeeRepository employeeRepo;
    private final HrTaxSettingsRepository settingsRepo;
    private final HrTaxCertificateRepository certRepo;
    private final HrTaxCertificateLineRepository lineRepo;

    // ── Settings ────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Settings settings(TenantId tenantId) {
        HrTaxSettings s = settingsRepo.findById(tenantId.getValue()).orElse(null);
        return s == null ? new Settings(null, null, null, TaxCertificateRules.PENSION)
                : new Settings(s.getPayeReference(), s.getSdlReference(), s.getUifReference(), s.getRetirementFundType());
    }

    @Transactional
    public Settings saveSettings(TenantId tenantId, Settings req, UUID actor) {
        String fund = req.retirementFundType() == null ? TaxCertificateRules.PENSION : req.retirementFundType();
        String problem = TaxCertificateRules.settingsProblem(req.payeReference(), fund);
        if (problem != null) throw new IllegalArgumentException(problem);
        HrTaxSettings s = settingsRepo.findById(tenantId.getValue()).orElseGet(() -> HrTaxSettings.of(tenantId.getValue()));
        s.update(req.payeReference(), req.sdlReference(), req.uifReference(), fund, actor);
        settingsRepo.save(s);
        return settings(tenantId);
    }

    // ── Preparing ───────────────────────────────────────────────────────────────────────────────

    /** Everything worked out for a reconciliation, before anything is issued. */
    private record Prepared(int taxYear, String period, TaxCertificateRules.Window window, int runs, Settings settings,
                            List<TaxCertificateRules.Problem> general, List<TaxCertificateRules.ReconLine> recon,
                            List<Draft> drafts) {
        boolean hasErrors() {
            return general.stream().anyMatch(TaxCertificateRules.Problem::isError)
                    || drafts.stream().anyMatch(d -> d.problems().stream().anyMatch(TaxCertificateRules.Problem::isError));
        }
    }

    private record Draft(HrEmployee employee, Totals totals, List<TaxCertificateRules.Line> lines, String type,
                         List<TaxCertificateRules.Problem> problems, HrTaxCertificate existing, String state) {}

    private Prepared prepare(TenantId tenantId, int taxYear, String period) {
        checkYear(taxYear);
        TaxCertificateRules.Window window = TaxCertificateRules.window(taxYear, period);
        Settings settings = settings(tenantId);
        List<TaxCertificateRules.Problem> general = new ArrayList<>(
                TaxCertificateRules.employerProblems(settings.payeReference(), settings.sdlReference(), settings.uifReference()));

        List<HrPayRun> runs = payRunRepo.paidBetween(tenantId, window.start(), window.end()).stream()
                .filter(r -> !PayRunWorkflowRules.CANCELLED.equals(r.getStatus())).toList();
        if (runs.isEmpty()) general.add(new TaxCertificateRules.Problem("ERROR", "Pay runs", "There are no pay runs paid between "
                + window.start() + " and " + window.end() + ", so there is nothing to certify."));

        List<HrPayRun> issued = new ArrayList<>();
        for (HrPayRun r : runs) {
            if (PayRunWorkflowRules.isIssued(r.getStatus())) issued.add(r);
            else general.add(new TaxCertificateRules.Problem("ERROR", r.getPayRunNumber(),
                    "This pay run is " + r.getStatus().toLowerCase() + ". Finalise it before certificates are prepared; it is left out of the figures below."));
        }
        Set<UUID> issuedIds = issued.stream().map(HrPayRun::getId).collect(Collectors.toSet());

        // EMP201 returns of the included runs.
        Map<UUID, List<HrEmp201>> emp201ByRun = issuedIds.isEmpty() ? Map.of() : emp201Repo.forPayRuns(tenantId, issuedIds).stream()
                .collect(Collectors.groupingBy(HrEmp201::getPayRunId));
        BigDecimal e201Paye = BigDecimal.ZERO, e201Uif = BigDecimal.ZERO, e201Sdl = BigDecimal.ZERO;
        for (HrPayRun r : issued) {
            List<HrEmp201> returns = emp201ByRun.getOrDefault(r.getId(), List.of());
            if (returns.isEmpty()) {
                general.add(new TaxCertificateRules.Problem("ERROR", r.getPayRunNumber(), "No EMP201 exists for this pay run."));
                continue;
            }
            for (HrEmp201 e : returns) {
                e201Paye = e201Paye.add(e.getTotalPaye());
                e201Uif = e201Uif.add(e.getTotalUif());
                e201Sdl = e201Sdl.add(e.getTotalSdl());
                if ("DRAFT".equals(e.getStatus()))
                    general.add(new TaxCertificateRules.Problem("WARNING", r.getPayRunNumber(), "The EMP201 for this run is still a draft: it has not been marked as submitted to SARS."));
            }
        }

        List<HrPayslip> slips = issuedIds.isEmpty() ? List.of() : payslipRepo.findByPayRuns(issuedIds);
        // A run's own totals should equal its payslips.
        Map<UUID, List<HrPayslip>> byRun = slips.stream().collect(Collectors.groupingBy(HrPayslip::getPayRunId));
        for (HrPayRun r : issued) {
            List<HrPayslip> rs = byRun.getOrDefault(r.getId(), List.of());
            BigDecimal paye = sum(rs, HrPayslip::getPayeAmount);
            if (paye.compareTo(r.getTotalPaye()) != 0)
                general.add(new TaxCertificateRules.Problem("WARNING", r.getPayRunNumber(), "The PAYE total of the run (" + TaxCertificateRules.cents(r.getTotalPaye())
                        + ") is not the sum of its payslips (" + TaxCertificateRules.cents(paye) + ")."));
        }

        Map<UUID, List<HrPayslip>> byEmployee = slips.stream().collect(Collectors.groupingBy(HrPayslip::getEmployeeId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, HrEmployee> employees = employeeRepo.findAllById(byEmployee.keySet()).stream()
                .filter(e -> tenantId.getValue().equals(e.getTenantId()))
                .collect(Collectors.toMap(HrEmployee::getId, e -> e));
        Map<UUID, HrTaxCertificate> live = certRepo.forReconciliation(tenantId, taxYear, period).stream()
                .filter(c -> "ISSUED".equals(c.getStatus())).collect(Collectors.toMap(HrTaxCertificate::getEmployeeId, c -> c, (a, b) -> b));
        Map<UUID, List<HrTaxCertificateLine>> liveLines = live.isEmpty() ? Map.of() : lineRepo.forCertificates(
                live.values().stream().map(HrTaxCertificate::getId).toList()).stream().collect(Collectors.groupingBy(HrTaxCertificateLine::getCertificateId));

        LocalDate today = LocalDate.now();
        List<Draft> drafts = new ArrayList<>();
        BigDecimal slipPaye = BigDecimal.ZERO, slipUif = BigDecimal.ZERO, slipSdl = BigDecimal.ZERO;
        BigDecimal certPaye = BigDecimal.ZERO, certUif = BigDecimal.ZERO, certSdl = BigDecimal.ZERO;
        for (var entry : byEmployee.entrySet()) {
            HrEmployee emp = employees.get(entry.getKey());
            if (emp == null) {
                general.add(new TaxCertificateRules.Problem("ERROR", entry.getKey().toString(), "Payslips exist for an employee record that cannot be found."));
                continue;
            }
            Totals t = totals(entry.getValue());
            slipPaye = slipPaye.add(t.paye());
            slipUif = slipUif.add(t.uif());
            slipSdl = slipSdl.add(t.sdl());
            List<TaxCertificateRules.Line> lines = TaxCertificateRules.lines(t, settings.retirementFundType());
            for (var l : lines) {
                if ("4102".equals(l.code())) certPaye = certPaye.add(l.amount());
                if ("4141".equals(l.code())) certUif = certUif.add(l.amount());
                if ("4142".equals(l.code())) certSdl = certSdl.add(l.amount());
            }
            String subject = emp.getFullName() + " (" + emp.getEmployeeNumber() + ")";
            List<TaxCertificateRules.Problem> problems = TaxCertificateRules.employeeProblems(subject, emp.getFirstName(), emp.getLastName(),
                    emp.getIdNumber(), emp.getTaxNumber(), today, t, lines);
            HrTaxCertificate existing = live.get(emp.getId());
            String state = existing == null ? "NEW" : sameLines(lines, liveLines.getOrDefault(existing.getId(), List.of())) ? "UNCHANGED" : "CHANGED";
            drafts.add(new Draft(emp, t, lines, TaxCertificateRules.certificateType(t), problems, existing, state));
        }
        drafts.sort(Comparator.comparing((Draft d) -> d.employee().getLastName() == null ? "" : d.employee().getLastName())
                .thenComparing(d -> d.employee().getFirstName() == null ? "" : d.employee().getFirstName()));

        List<TaxCertificateRules.ReconLine> recon = List.of(
                TaxCertificateRules.reconLine("PAYE", slipPaye, certPaye, e201Paye),
                TaxCertificateRules.reconLine("UIF", slipUif, certUif, e201Uif),
                TaxCertificateRules.reconLine("SDL", slipSdl, certSdl, e201Sdl));
        for (var r : recon) if (!r.balanced())
            general.add(new TaxCertificateRules.Problem("ERROR", r.tax(), r.tax() + " does not balance between the payslips, the certificates and the EMP201 returns (difference "
                    + r.difference() + "). A run may have been recalculated after its EMP201 was made."));
        general.add(new TaxCertificateRules.Problem("INFO", "Payments", "Payments made to SARS are not recorded in HandyFlow, so this does not compare with what was paid. "
                + "Check the totals above against your SARS statement of account."));

        return new Prepared(taxYear, period, window, issued.size(), settings, general, recon, drafts);
    }

    private static BigDecimal sum(List<HrPayslip> l, java.util.function.Function<HrPayslip, BigDecimal> f) {
        return l.stream().map(p -> f.apply(p) == null ? BigDecimal.ZERO : f.apply(p)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Totals totals(List<HrPayslip> l) {
        int withoutCredit = (int) l.stream().filter(p -> p.getMedicalTaxCredit() == null).count();
        // Annual leave paid out on the last pay, and back pay, are reported with the basic salary.
        return new Totals(sum(l, HrPayslip::getGrossSalary).add(sum(l, HrPayslip::getLeavePayoutAmount)).add(sum(l, HrPayslip::getBackPayAmount)), sum(l, HrPayslip::getOvertimeAmount), sum(l, HrPayslip::getBonusAmount),
                sum(l, HrPayslip::getOtherEarnings), sum(l, HrPayslip::getTravelAllowance), sum(l, HrPayslip::getPension),
                sum(l, HrPayslip::getMedicalAid), sum(l, HrPayslip::getPayeAmount), sum(l, HrPayslip::getUifEmployee),
                sum(l, HrPayslip::getUifEmployer), sum(l, HrPayslip::getSdlAmount), sum(l, HrPayslip::getMedicalTaxCredit),
                l.size(), withoutCredit, sum(l, HrPayslip::getEmployerMedical), sum(l, HrPayslip::getEmployerRetirement), sum(l, HrPayslip::getOtherFringe));
    }

    private static boolean sameLines(List<TaxCertificateRules.Line> lines, List<HrTaxCertificateLine> stored) {
        if (lines.size() != stored.size()) return false;
        Map<String, BigDecimal> have = stored.stream().collect(Collectors.toMap(HrTaxCertificateLine::getCode, HrTaxCertificateLine::getAmount, (a, b) -> a));
        for (var l : lines) {
            BigDecimal a = have.get(l.code());
            if (a == null || a.compareTo(l.amount()) != 0) return false;
        }
        return true;
    }

    private static void checkYear(int taxYear) {
        int now = StatutoryYearRules.taxYearOf(LocalDate.now());
        if (taxYear < 2010 || taxYear > now + 1) throw new IllegalArgumentException("Choose a tax year between 2010 and " + (now + 1) + ".");
    }

    // ── Reading ─────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Reconciliation reconciliation(TenantId tenantId, int taxYear, String period) {
        Prepared p = prepare(tenantId, taxYear, period);
        List<Certificate> certs = p.drafts().stream().map(d -> draftResponse(p, d)).toList();
        return new Reconciliation(taxYear, period, p.window().start(), p.window().end(), p.runs(), !p.hasErrors() && !p.drafts().isEmpty(),
                TaxCertificateRules.INTERIM.equals(period), p.general().stream().map(TaxCertificateService::problem).toList(),
                p.recon().stream().map(r -> new Recon(r.tax(), r.payslips(), r.certificates(), r.emp201(), r.difference(), r.balanced())).toList(),
                certs, TaxCertificateRules.NOT_PRODUCED);
    }

    private Certificate draftResponse(Prepared p, Draft d) {
        HrEmployee e = d.employee();
        HrTaxCertificate ex = d.existing();
        return new Certificate(ex == null ? null : ex.getId(), p.taxYear(), p.period(), e.getId(), e.getEmployeeNumber(), e.getFullName(), e.getIdNumber(), e.getTaxNumber(),
                ex == null ? null : ex.getCertificateNumber(), d.type(), ex == null ? 1 : ex.getVersion() + ("CHANGED".equals(d.state()) ? 1 : 0), "DRAFT", d.state(),
                p.window().start(), p.window().end(), d.totals().payslips(), ex == null ? null : ex.getIssuedAt(), null, null, ex == null ? null : ex.getReleasedAt(),
                d.lines().stream().map(l -> new Line(l.code(), l.description(), l.amount())).toList(),
                d.problems().stream().map(TaxCertificateService::problem).toList());
    }

    private static TaxCertificateDtos.Problem problem(TaxCertificateRules.Problem p) {
        return new TaxCertificateDtos.Problem(p.severity(), p.subject(), p.message());
    }

    /** Issued certificates of a reconciliation, with their lines. */
    @Transactional(readOnly = true)
    public List<Certificate> issuedCertificates(TenantId tenantId, int taxYear, String period) {
        TaxCertificateRules.window(taxYear, period);
        return respond(certRepo.forReconciliation(tenantId, taxYear, period));
    }

    @Transactional(readOnly = true)
    public Certificate get(TenantId tenantId, UUID id) {
        HrTaxCertificate c = certRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("TaxCertificate", id.toString()));
        return respond(List.of(c)).get(0);
    }

    /** Annual certificates released to an employee. Interim ones and unreleased ones are never shown. */
    @Transactional(readOnly = true)
    public List<Certificate> releasedFor(TenantId tenantId, UUID employeeId) {
        return respond(certRepo.releasedFor(tenantId, employeeId));
    }

    private List<Certificate> respond(List<HrTaxCertificate> certs) {
        if (certs.isEmpty()) return List.of();
        Map<UUID, List<HrTaxCertificateLine>> lines = lineRepo.forCertificates(certs.stream().map(HrTaxCertificate::getId).toList())
                .stream().collect(Collectors.groupingBy(HrTaxCertificateLine::getCertificateId));
        return certs.stream().map(c -> new Certificate(c.getId(), c.getTaxYear(), c.getPeriod(), c.getEmployeeId(), c.getEmployeeNumber(),
                (c.getFirstNames() + " " + c.getSurname()).trim(), c.getIdNumber(), c.getTaxNumber(), c.getCertificateNumber(), c.getCertificateType(),
                c.getVersion(), c.getStatus(), c.getStatus(), c.getPeriodFrom(), c.getPeriodTo(), c.getPayslipCount(), c.getIssuedAt(), c.getCancelledAt(),
                c.getCancelReason(), c.getReleasedAt(),
                lines.getOrDefault(c.getId(), List.of()).stream().map(l -> new Line(l.getCode(), l.getDescription(), l.getAmount())).toList(), List.of())).toList();
    }

    // ── Issuing ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Issues a certificate for everyone with payslips in the period. An employee whose certificate is already issued with the
     * same figures is left alone; one whose figures changed gets the next version (the old one is cancelled), which needs a reason.
     */
    @Transactional
    public IssueResult issue(TenantId tenantId, int taxYear, String period, String reason, UUID actor) {
        if (actor == null) throw new org.springframework.security.access.AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        Prepared p = prepare(tenantId, taxYear, period);
        if (p.drafts().isEmpty()) throw new IllegalStateException("There are no payslips in this period, so there is nothing to issue.");
        if (p.hasErrors()) {
            List<String> errors = new ArrayList<>();
            p.general().stream().filter(TaxCertificateRules.Problem::isError).forEach(x -> errors.add(x.subject() + ": " + x.message()));
            p.drafts().forEach(d -> d.problems().stream().filter(TaxCertificateRules.Problem::isError).forEach(x -> errors.add(x.subject() + ": " + x.message())));
            throw new IllegalStateException("Certificates cannot be issued yet. " + errors.size() + " problem(s), for example: "
                    + errors.stream().limit(3).collect(Collectors.joining(" | ")));
        }
        boolean anyChanged = p.drafts().stream().anyMatch(d -> "CHANGED".equals(d.state()));
        if (anyChanged && (reason == null || reason.isBlank()))
            throw new IllegalArgumentException("Some certificates have already been issued with different figures. Give the reason they are being replaced.");

        int issued = 0, unchanged = 0, replaced = 0;
        for (Draft d : p.drafts()) {
            if ("UNCHANGED".equals(d.state())) { unchanged++; continue; }
            int version = 1;
            UUID supersedes = null;
            if (d.existing() != null) {
                HrTaxCertificate old = d.existing();
                old.cancel(actor, "Replaced: " + reason.trim());
                certRepo.saveAndFlush(old);
                version = old.getVersion() + 1;
                supersedes = old.getId();
                replaced++;
            }
            String number = TaxCertificateRules.certificateNumber(p.settings().payeReference(), taxYear, period, certRepo.nextSequence());
            HrTaxCertificate cert = HrTaxCertificate.issue(tenantId.getValue(), taxYear, period, d.employee(), number, d.type(), version,
                    p.settings().payeReference().replaceAll("\\s", ""), p.window().start(), p.window().end(), d.totals().payslips(),
                    reason == null || reason.isBlank() ? null : reason.trim(), supersedes, actor);
            certRepo.save(cert);
            for (var l : d.lines()) lineRepo.save(HrTaxCertificateLine.of(tenantId.getValue(), cert.getId(), l));
            issued++;
        }
        log.info("Issued tax certificates year={} period={} new={} unchanged={} replaced={}", taxYear, period, issued, unchanged, replaced);
        return new IssueResult(issued, unchanged, replaced);
    }

    @Transactional
    public Certificate cancel(TenantId tenantId, UUID id, String reason, UUID actor) {
        if (actor == null) throw new org.springframework.security.access.AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give the reason for cancelling the certificate.");
        HrTaxCertificate c = certRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("TaxCertificate", id.toString()));
        if (!"ISSUED".equals(c.getStatus())) throw new IllegalStateException("This certificate is already cancelled.");
        c.cancel(actor, reason.trim());
        certRepo.save(c);
        return respond(List.of(c)).get(0);
    }

    /**
     * Makes the annual certificates of a year visible to the employees. SARS: interim certificates are for SARS only, and final ones
     * go to employees once the annual reconciliation is done, so only annual certificates can be released.
     */
    @Transactional
    public int release(TenantId tenantId, int taxYear, UUID actor) {
        if (actor == null) throw new org.springframework.security.access.AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        checkYear(taxYear);
        int n = 0;
        for (HrTaxCertificate c : certRepo.forReconciliation(tenantId, taxYear, TaxCertificateRules.ANNUAL)) {
            if (!"ISSUED".equals(c.getStatus()) || c.getReleasedAt() != null) continue;
            c.release(actor);
            certRepo.save(c);
            n++;
        }
        if (n == 0) throw new IllegalStateException("There are no issued annual certificates waiting to be released for " + taxYear + ".");
        return n;
    }

    // ── Export ──────────────────────────────────────────────────────────────────────────────────

    /** A spreadsheet of the issued certificates, one row per code. A workpaper for completing the submission, not a SARS import file. */
    @Transactional(readOnly = true)
    public String csv(TenantId tenantId, int taxYear, String period) {
        TaxCertificateRules.window(taxYear, period);
        List<HrTaxCertificate> certs = certRepo.forReconciliation(tenantId, taxYear, period);
        Map<UUID, List<HrTaxCertificateLine>> lines = certs.isEmpty() ? Map.of() : lineRepo.forCertificates(certs.stream().map(HrTaxCertificate::getId).toList())
                .stream().collect(Collectors.groupingBy(HrTaxCertificateLine::getCertificateId));
        StringBuilder sb = new StringBuilder("certificate_number,type,version,status,employee_number,surname,first_names,id_number,tax_number,code,description,amount\r\n");
        for (HrTaxCertificate c : certs) {
            for (HrTaxCertificateLine l : lines.getOrDefault(c.getId(), List.of())) {
                sb.append(String.join(",", cell(c.getCertificateNumber()), cell(c.getCertificateType()), String.valueOf(c.getVersion()), cell(c.getStatus()),
                        cell(c.getEmployeeNumber()), cell(c.getSurname()), cell(c.getFirstNames()), cell(c.getIdNumber()), cell(c.getTaxNumber()),
                        l.getCode(), cell(l.getDescription()), l.getAmount().toPlainString())).append("\r\n");
            }
        }
        return sb.toString();
    }

    /** CSV cell: quoted, and a leading = + - @ is defused so a spreadsheet does not run it as a formula. */
    static String cell(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
