package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.PayRunPaymentDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The bank payment file for a finalised pay run and what happens to each payment afterwards. Making up the payments copies each
 * employee's bank details, leaves out (and lists) anyone whose details are missing or malformed, and can be repeated until a payment
 * has been confirmed. Every step is written to the pay run's history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayRunPaymentService {

    private final HrPayRunRepository payRunRepo;
    private final HrPayslipRepository payslipRepo;
    private final HrEmployeeRepository employeeRepo;
    private final HrPayRunPaymentRepository paymentRepo;
    private final HrPayRunEventRepository eventRepo;
    private final HrPayrollSettingsRepository settingsRepo;

    @Transactional(readOnly = true)
    public Summary summary(TenantId tenantId, UUID runId) {
        HrPayRun run = load(tenantId, runId);
        return toSummary(run, paymentRepo.forRun(tenantId, runId), employees(tenantId, paymentRepo.forRun(tenantId, runId)), List.of());
    }

    /** Make up (or remake) the payments from the payslips. Returns the people who could not be included, with the reason. */
    @Transactional
    public Summary makeUp(TenantId tenantId, UUID runId, UUID actor) {
        requireActor(actor);
        HrPayRun run = load(tenantId, runId);
        List<HrPayRunPayment> existing = paymentRepo.forRun(tenantId, runId);
        long nonPending = existing.stream().filter(p -> !PayrollPaymentRules.PENDING.equals(p.getStatus())).count();
        String problem = PayrollPaymentRules.generateProblem(run.getStatus(), nonPending);
        if (problem != null) throw new IllegalStateException(problem);

        List<HrPayslip> slips = payslipRepo.findByPayRun(runId);
        Map<UUID, HrEmployee> emps = employeeRepo.findAllByIds(tenantId, slips.stream().map(HrPayslip::getEmployeeId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(HrEmployee::getId, Function.identity()));

        if (!existing.isEmpty()) paymentRepo.deleteAll(existing);
        List<HrPayRunPayment> made = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (HrPayslip s : slips) {
            BigDecimal net = PayrollPaymentRules.money(s.getNetPay());
            HrEmployee e = emps.get(s.getEmployeeId());
            if (e == null) { skipped.add(new Skipped(s.getEmployeeId(), null, null, net, "The employee record could not be found")); continue; }
            if (net.signum() <= 0) {
                skipped.add(new Skipped(e.getId(), e.getEmployeeNumber(), e.getFullName(), net,
                        net.signum() == 0 ? "Net pay is zero, so there is nothing to pay" : "Net pay is negative. Fix the payslip before paying"));
                continue;
            }
            String bad = PayrollPaymentRules.bankProblem(e.getFullName(), e.getBankAccountNumber(), e.getBankBranchCode());
            if (bad != null) { skipped.add(new Skipped(e.getId(), e.getEmployeeNumber(), e.getFullName(), net, "Bank details: " + bad)); continue; }
            made.add(HrPayRunPayment.pending(tenantId.getValue(), runId, s.getId(), e.getId(), net, e.getFullName(), e.getBankName(),
                    e.getBankBranchCode(), e.getBankAccountNumber(), e.getBankAccountType(), PayrollPaymentRules.reference(run.getPayRunNumber(), e.getEmployeeNumber()), actor));
        }
        paymentRepo.saveAll(made);
        BigDecimal total = made.stream().map(HrPayRunPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        event(tenantId, run, "PAYMENTS_MADE_UP", actor, made.size() + " payments, R" + total.toPlainString()
                + (skipped.isEmpty() ? "" : ", " + skipped.size() + " left out"));
        return toSummary(run, made, emps, skipped);
    }

    /** The generic CSV for the pending payments. */
    @Transactional
    public FileResult file(TenantId tenantId, UUID runId, UUID actor) {
        return file(tenantId, runId, actor, BankFileRules.GENERIC_CSV);
    }

    /**
     * The bank file for the pending payments, in the layout asked for. Recorded in the history, because it holds everyone's bank details.
     * Nothing is marked as downloaded when the layout cannot be written (for example the company's own account is missing).
     */
    @Transactional
    public FileResult file(TenantId tenantId, UUID runId, UUID actor, String layout) {
        requireActor(actor);
        HrPayRun run = load(tenantId, runId);
        HrPayrollSettings settings = settingsRepo.findById(tenantId.getValue()).orElse(null);
        String branch = settings == null ? null : settings.getBankNominatedBranch(), account = settings == null ? null : settings.getBankNominatedAccount();
        String problem = BankFileRules.layoutProblem(layout, branch, account);
        if (problem != null) throw new IllegalStateException(problem);
        List<HrPayRunPayment> pending = paymentRepo.forRun(tenantId, runId).stream()
                .filter(p -> PayrollPaymentRules.PENDING.equals(p.getStatus())).toList();
        if (pending.isEmpty()) throw new IllegalStateException("There are no pending payments. Make up the payments first.");
        List<PayrollPaymentRules.Line> lines = pending.stream().map(p -> new PayrollPaymentRules.Line(p.getBeneficiaryName(), p.getBankName(),
                p.getBranchCode(), p.getAccountNumber(), p.getAmount(), p.getReference(), p.getAccountType())).toList();
        String content = switch (layout) {
            case BankFileRules.BANKSERV_ACB -> BankFileRules.bankserv(lines, branch, account,
                    BankFileRules.actionDate(run.getPayDate(), java.time.LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg"))));
            case BankFileRules.ABSA_BIO -> BankFileRules.absaBio(lines);
            default -> PayrollPaymentRules.csv(lines);
        };
        pending.forEach(p -> p.markExported(actor));
        paymentRepo.saveAll(pending);
        event(tenantId, run, "PAYMENT_FILE_DOWNLOADED", actor, pending.size() + " payments, R" + BankFileRules.total(lines).toPlainString() + ", " + layout);
        return new FileResult(BankFileRules.filename(layout, run.getPayRunNumber()), content, layout);
    }

    /** Payroll confirms that the bank accepted the file: every pending payment becomes paid. */
    @Transactional
    public Summary confirmPaid(TenantId tenantId, UUID runId, UUID actor, String note) {
        requireActor(actor);
        HrPayRun run = load(tenantId, runId);
        List<HrPayRunPayment> all = paymentRepo.forRun(tenantId, runId);
        List<HrPayRunPayment> pending = all.stream().filter(p -> PayrollPaymentRules.PENDING.equals(p.getStatus())).toList();
        String problem = PayrollPaymentRules.markPaidProblem(pending.size(), pending.stream().filter(p -> p.getExportedAt() == null).count());
        if (problem != null) throw new IllegalStateException(problem);
        boolean secondCheck = settingsRepo.findById(tenantId.getValue()).map(HrPayrollSettings::isBankFileSecondCheck).orElse(false);
        String confirmer = PayrollPaymentRules.confirmerProblem(secondCheck, actor,
                pending.stream().map(HrPayRunPayment::getExportedBy).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        if (confirmer != null) throw new IllegalStateException(confirmer);
        pending.forEach(p -> p.markPaid(actor));
        paymentRepo.saveAll(pending);
        event(tenantId, run, "PAYMENTS_CONFIRMED_PAID", actor, pending.size() + " payments" + (note == null || note.isBlank() ? "" : ". " + note.trim()));
        return toSummary(run, all, employees(tenantId, all), List.of());
    }

    /** The bank bounced one payment. It stays in the record with the reason; paying it again is done outside this file. */
    @Transactional
    public Summary markReturned(TenantId tenantId, UUID paymentId, String reason, UUID actor) {
        requireActor(actor);
        HrPayRunPayment p = paymentRepo.findByTenantAndId(tenantId, paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId.toString()));
        String problem = PayrollPaymentRules.returnProblem(p.getStatus(), reason);
        if (problem != null) throw new IllegalArgumentException(problem);
        HrPayRun run = load(tenantId, p.getPayRunId());
        p.markReturned(actor, reason);
        paymentRepo.save(p);
        event(tenantId, run, "PAYMENT_RETURNED", actor, p.getBeneficiaryName() + " R" + p.getAmount().toPlainString() + ": " + reason.trim());
        List<HrPayRunPayment> all = paymentRepo.forRun(tenantId, run.getId());
        return toSummary(run, all, employees(tenantId, all), List.of());
    }

    /** What the bank's file would change, without changing anything. */
    @Transactional(readOnly = true)
    public ReturnPreview previewReturns(TenantId tenantId, UUID runId, String content) {
        load(tenantId, runId);
        return preview(read(tenantId, runId, content));
    }

    /** Marks every payment the bank's file clearly names as returned, with the reason from the file. Rows that did not match are left alone. */
    @Transactional
    public Summary applyReturns(TenantId tenantId, UUID runId, String content, UUID actor) {
        requireActor(actor);
        HrPayRun run = load(tenantId, runId);
        Reading reading = read(tenantId, runId, content);
        List<BankReturnRules.Match> matched = reading.matches().stream().filter(m -> BankReturnRules.MATCHED.equals(m.outcome())).toList();
        if (matched.isEmpty()) throw new IllegalStateException("No row of the file matched a payment of this run, so nothing was changed.");
        Map<UUID, HrPayRunPayment> byId = reading.payments().stream().collect(Collectors.toMap(HrPayRunPayment::getId, Function.identity()));
        BigDecimal total = BigDecimal.ZERO;
        for (BankReturnRules.Match m : matched) {
            HrPayRunPayment p = byId.get(m.paymentId());
            String reason = BankReturnRules.reasonFor(m.row());
            String problem = PayrollPaymentRules.returnProblem(p.getStatus(), reason);
            if (problem != null) throw new IllegalStateException(p.getBeneficiaryName() + ": " + problem);
            p.markReturned(actor, reason);
            total = total.add(p.getAmount());
            event(tenantId, run, "PAYMENT_RETURNED", actor, p.getBeneficiaryName() + " R" + p.getAmount().toPlainString() + ": " + reason.trim() + " (bank file)");
        }
        paymentRepo.saveAll(matched.stream().map(m -> byId.get(m.paymentId())).toList());
        event(tenantId, run, "RETURNS_IMPORTED", actor, matched.size() + " of " + reading.matches().size() + " rows, R" + total.toPlainString());
        List<HrPayRunPayment> all = paymentRepo.forRun(tenantId, runId);
        return toSummary(run, all, employees(tenantId, all), List.of());
    }

    private record Reading(List<BankReturnRules.Match> matches, List<HrPayRunPayment> payments) {}

    private Reading read(TenantId tenantId, UUID runId, String content) {
        BankReturnRules.Parsed parsed = BankReturnRules.parse(content);
        if (parsed.problem() != null) throw new IllegalArgumentException(parsed.problem());
        List<HrPayRunPayment> payments = paymentRepo.forRun(tenantId, runId);
        List<BankReturnRules.Candidate> candidates = payments.stream().map(p -> new BankReturnRules.Candidate(p.getId(), p.getAccountNumber(), p.getAmount(),
                p.getReference(), p.getBeneficiaryName(), p.getStatus())).toList();
        return new Reading(BankReturnRules.match(parsed.rows(), candidates), payments);
    }

    private static ReturnPreview preview(Reading r) {
        Map<UUID, HrPayRunPayment> byId = r.payments().stream().collect(Collectors.toMap(HrPayRunPayment::getId, Function.identity()));
        List<ReturnLine> lines = r.matches().stream().map(m -> {
            HrPayRunPayment p = m.paymentId() == null ? null : byId.get(m.paymentId());
            return new ReturnLine(m.row().line(), mask(m.row().account()), m.row().amount(), m.row().name(), BankReturnRules.reasonFor(m.row()), m.outcome(),
                    BankReturnRules.MATCHED.equals(m.outcome()) ? m.paymentId() : null, p == null ? null : p.getBeneficiaryName());
        }).toList();
        return new ReturnPreview(lines.size(), (int) lines.stream().filter(l -> BankReturnRules.MATCHED.equals(l.outcome())).count(), lines);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Map<UUID, HrEmployee> employees(TenantId tenantId, List<HrPayRunPayment> payments) {
        if (payments.isEmpty()) return Map.of();
        return employeeRepo.findAllByIds(tenantId, payments.stream().map(HrPayRunPayment::getEmployeeId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(HrEmployee::getId, Function.identity()));
    }

    private Summary toSummary(HrPayRun run, List<HrPayRunPayment> payments, Map<UUID, HrEmployee> emps, List<Skipped> skipped) {
        int pending = 0, paid = 0, returned = 0;
        BigDecimal total = BigDecimal.ZERO, returnedTotal = BigDecimal.ZERO;
        boolean downloaded = !payments.isEmpty();
        for (HrPayRunPayment p : payments) {
            total = total.add(p.getAmount());
            switch (p.getStatus()) {
                case PayrollPaymentRules.PAID -> paid++;
                case PayrollPaymentRules.RETURNED -> { returned++; returnedTotal = returnedTotal.add(p.getAmount()); }
                default -> { pending++; if (p.getExportedAt() == null) downloaded = false; }
            }
        }
        boolean canMakeUp = PayRunWorkflowRules.isIssued(run.getStatus()) && paid + returned == 0;
        List<Payment> views = payments.stream().map(p -> {
            HrEmployee e = emps.get(p.getEmployeeId());
            return new Payment(p.getId(), p.getEmployeeId(), e == null ? null : e.getEmployeeNumber(), p.getBeneficiaryName(), p.getBankName(),
                    p.getBranchCode(), mask(p.getAccountNumber()), p.getAmount(), p.getReference(), p.getStatus(), p.getExportedAt() != null,
                    p.getPaidAt(), p.getReturnedAt(), p.getReturnReason(),
                    PayrollPaymentRules.PENDING.equals(p.getStatus()) || PayrollPaymentRules.PAID.equals(p.getStatus()));
        }).toList();
        return new Summary(run.getId(), run.getPayRunNumber(), run.getStatus(), payments.size(), total, pending, paid, returned, returnedTotal,
                downloaded, canMakeUp, skipped, views);
    }

    /** Only the last four digits are shown on screen; the file holds the whole number. */
    static String mask(String account) {
        if (account == null || account.length() <= 4) return account;
        return "••••" + account.substring(account.length() - 4);
    }

    private HrPayRun load(TenantId tenantId, UUID runId) {
        return payRunRepo.findByTenantAndId(tenantId, runId).orElseThrow(() -> new ResourceNotFoundException("PayRun", runId.toString()));
    }

    private void event(TenantId tenantId, HrPayRun run, String action, UUID actor, String note) {
        eventRepo.save(HrPayRunEvent.of(tenantId.getValue(), run.getId(), action, run.getStatus(), run.getStatus(), actor, note));
        log.info("Pay run {}: {} by {}", run.getPayRunNumber(), action, actor);
    }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }
}
