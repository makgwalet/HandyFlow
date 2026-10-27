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
                    e.getBankBranchCode(), e.getBankAccountNumber(), PayrollPaymentRules.reference(run.getPayRunNumber(), e.getEmployeeNumber()), actor));
        }
        paymentRepo.saveAll(made);
        BigDecimal total = made.stream().map(HrPayRunPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        event(tenantId, run, "PAYMENTS_MADE_UP", actor, made.size() + " payments, R" + total.toPlainString()
                + (skipped.isEmpty() ? "" : ", " + skipped.size() + " left out"));
        return toSummary(run, made, emps, skipped);
    }

    /** The CSV for the pending payments. Recorded in the history, because it holds everyone's bank details. */
    @Transactional
    public FileResult file(TenantId tenantId, UUID runId, UUID actor) {
        requireActor(actor);
        HrPayRun run = load(tenantId, runId);
        List<HrPayRunPayment> pending = paymentRepo.forRun(tenantId, runId).stream()
                .filter(p -> PayrollPaymentRules.PENDING.equals(p.getStatus())).toList();
        if (pending.isEmpty()) throw new IllegalStateException("There are no pending payments. Make up the payments first.");
        pending.forEach(HrPayRunPayment::markExported);
        paymentRepo.saveAll(pending);
        event(tenantId, run, "PAYMENT_FILE_DOWNLOADED", actor, pending.size() + " payments");
        List<PayrollPaymentRules.Line> lines = pending.stream().map(p -> new PayrollPaymentRules.Line(p.getBeneficiaryName(), p.getBankName(),
                p.getBranchCode(), p.getAccountNumber(), p.getAmount(), p.getReference())).toList();
        return new FileResult(PayrollPaymentRules.filename(run.getPayRunNumber()), PayrollPaymentRules.csv(lines));
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
