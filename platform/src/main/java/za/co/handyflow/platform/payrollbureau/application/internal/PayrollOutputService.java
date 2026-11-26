package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.payrollbureau.domain.model.*;
import za.co.handyflow.platform.payrollbureau.domain.repository.*;
import za.co.handyflow.platform.payrollbureau.domain.rules.BankFileRules;
import za.co.handyflow.platform.payrollbureau.domain.rules.JournalRules;
import za.co.handyflow.platform.payrollbureau.domain.rules.RunLifecycleRules;
import za.co.handyflow.platform.payrollbureau.dto.PayBankFileDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** What a finalised pay run produces for others: the bank payment file and the payroll journal (phase 3). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollOutputService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final PayRunRepository payRunRepo;
    private final PayslipRepository payslipRepo;
    private final PayEmployeeRepository employeeRepo;
    private final PayBankFileRepository bankFileRepo;
    private final PayBankFileDownloadRepository downloadRepo;

    private PayRun loadRun(TenantId tenantId, UUID runId) {
        return payRunRepo.findByTenantAndId(tenantId.getValue(), runId)
                .orElseThrow(() -> new ResourceNotFoundException("PayRun", runId.toString()));
    }

    // ── bank file ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BankFileResponse> bankFiles(TenantId tenantId, UUID runId) {
        loadRun(tenantId, runId);
        return bankFileRepo.findByRun(tenantId.getValue(), runId).stream().map(f -> toResponse(tenantId, f)).toList();
    }

    /** Makes the bank file for a finalised run. The first needs nothing more; a replacement needs a reason. */
    @Transactional
    public BankFileResponse createBankFile(TenantId tenantId, UUID runId, String reason) {
        PayRun run = loadRun(tenantId, runId);
        if (!RunLifecycleRules.FINALISED.equals(run.getStatus())) {
            throw new HandyFlowException("A bank file can only be made for a finalised pay run. This one is " + run.getStatus() + ".",
                    HttpStatus.BAD_REQUEST, "PAYRUN_NOT_FINALISED");
        }
        List<PayBankFile> existing = bankFileRepo.findByRun(tenantId.getValue(), runId);
        Optional<PayBankFile> active = existing.stream().filter(f -> "ACTIVE".equals(f.getStatus())).findFirst();
        String problem = BankFileRules.supersedeProblem(active.isPresent(), reason);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "REASON_REQUIRED");

        List<BankFileRules.Payment> payments = new ArrayList<>();
        for (Payslip p : payslipRepo.findByPayRun(tenantId.getValue(), runId)) {
            PayEmployee e = employeeRepo.findById(p.getPayEmployeeId()).orElse(null);
            if (e == null) continue;
            payments.add(new BankFileRules.Payment(e.getEmployeeNumber(), e.getFullName(), e.getBankName(), e.getBankBranchCode(),
                    e.getBankAccountNumber(), p.getNetPay(), run.getPayRunNumber() + " " + e.getEmployeeNumber()));
        }
        BankFileRules.Result result = BankFileRules.build(payments);
        if (result.paymentCount() == 0) {
            throw new HandyFlowException("Nobody in this pay run can be paid by bank file: " + String.join(" ", result.excluded()),
                    HttpStatus.UNPROCESSABLE_ENTITY, "NO_PAYMENTS");
        }
        active.ifPresent(f -> { f.supersede(); bankFileRepo.saveAndFlush(f); });
        int number = existing.stream().mapToInt(PayBankFile::getFileNumber).max().orElse(0) + 1;
        PayBankFile file = PayBankFile.create(tenantId.getValue(), runId, number, result.paymentCount(), result.total(),
                result.excluded().isEmpty() ? null : String.join("\n", result.excluded()), reason,
                TenantContext.getCurrentUserId(), result.csv());
        try {
            bankFileRepo.saveAndFlush(file);
        } catch (DataIntegrityViolationException e) {
            throw new HandyFlowException("Another bank file was made for this pay run at the same moment. Look at the list and try again.",
                    HttpStatus.CONFLICT, "BANK_FILE_CONFLICT");
        }
        log.info("Payroll bureau bank file made run={} file={} payments={} total={} by={}", run.getPayRunNumber(), number,
                result.paymentCount(), result.total(), TenantContext.getCurrentUserId());
        return toResponse(tenantId, file);
    }

    /** Hands over the file and records who took it. */
    @Transactional
    public GeneratedFile downloadBankFile(TenantId tenantId, UUID runId, UUID fileId) {
        PayRun run = loadRun(tenantId, runId);
        PayBankFile f = bankFileRepo.findByRunAndId(tenantId.getValue(), runId, fileId)
                .orElseThrow(() -> new ResourceNotFoundException("PayBankFile", fileId.toString()));
        downloadRepo.save(PayBankFileDownload.of(tenantId.getValue(), f.getId(), TenantContext.getCurrentUserId()));
        log.info("Payroll bureau bank file downloaded run={} file={} by={}", run.getPayRunNumber(), f.getFileNumber(),
                TenantContext.getCurrentUserId());
        return new GeneratedFile(run.getPayRunNumber() + "-bank-" + f.getFileNumber() + ".csv", f.getContent().getBytes(StandardCharsets.UTF_8));
    }

    private BankFileResponse toResponse(TenantId tenantId, PayBankFile f) {
        List<String> excluded = f.getExcludedNote() == null ? List.of() : Arrays.asList(f.getExcludedNote().split("\n"));
        return new BankFileResponse(f.getId(), f.getFileNumber(), f.getStatus(), f.getPaymentCount(), f.getTotalAmount(), excluded,
                f.getReason(), f.getCreatedAt(), downloadRepo.countForFile(tenantId.getValue(), f.getId()));
    }

    // ── journal ────────────────────────────────────────────────────────────────

    /** The run's journal for the client's accounting package. A reversed run gives the opposite entries. */
    @Transactional(readOnly = true)
    public GeneratedFile journal(TenantId tenantId, UUID runId) {
        PayRun run = loadRun(tenantId, runId);
        boolean reversal = RunLifecycleRules.REVERSED.equals(run.getStatus());
        if (!reversal && !RunLifecycleRules.FINALISED.equals(run.getStatus())) {
            throw new HandyFlowException("A journal can only be made for a finalised or reversed pay run. This one is " + run.getStatus() + ".",
                    HttpStatus.BAD_REQUEST, "PAYRUN_NOT_FINALISED");
        }
        BigDecimal earnings = BigDecimal.ZERO, paye = BigDecimal.ZERO, uifEe = BigDecimal.ZERO, uifEr = BigDecimal.ZERO, sdl = BigDecimal.ZERO,
                medical = BigDecimal.ZERO, pension = BigDecimal.ZERO, other = BigDecimal.ZERO, net = BigDecimal.ZERO;
        for (Payslip p : payslipRepo.findByPayRun(tenantId.getValue(), runId)) {
            earnings = earnings.add(p.getTotalEarnings());
            paye = paye.add(p.getPayeAmount());
            uifEe = uifEe.add(p.getUifEmployee());
            uifEr = uifEr.add(p.getUifEmployer());
            sdl = sdl.add(p.getSdlAmount());
            medical = medical.add(p.getMedicalAid());
            pension = pension.add(p.getPension());
            other = other.add(p.getExtraDeductions());
            net = net.add(p.getNetPay());
        }
        List<JournalRules.Line> lines = JournalRules.lines(
                new JournalRules.Totals(earnings, paye, uifEe, uifEr, sdl, medical, pension, other, net), reversal);
        if (!JournalRules.balanced(lines)) {
            // Cannot happen while payslip totals are consistent; refuse rather than hand over a journal that does not balance.
            throw new HandyFlowException("This pay run's totals do not balance, so no journal was made. Check its payslips.",
                    HttpStatus.UNPROCESSABLE_ENTITY, "JOURNAL_UNBALANCED");
        }
        String csv = JournalRules.csv(run.getPayRunNumber(), run.getPeriodStart().format(DATE) + " to " + run.getPeriodEnd().format(DATE),
                run.getPayDate().format(DATE), reversal, lines);
        return new GeneratedFile(run.getPayRunNumber() + (reversal ? "-journal-reversal.csv" : "-journal.csv"), csv.getBytes(StandardCharsets.UTF_8));
    }
}
