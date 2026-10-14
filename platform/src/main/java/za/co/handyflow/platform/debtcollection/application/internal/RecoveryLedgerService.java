package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.application.internal.LedgerMath.Line;
import za.co.handyflow.platform.debtcollection.application.internal.LedgerMath.Totals;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.FeeRule;
import za.co.handyflow.platform.debtcollection.domain.model.LedgerEntryType;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentSource;
import za.co.handyflow.platform.debtcollection.domain.model.RecoveryEntry;
import za.co.handyflow.platform.debtcollection.domain.repository.FeeRuleRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.RecoveryEntryRepository;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.OutstandingInvoiceSummary;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The recovery ledger: payments, reversals, fee charges and fee waivers per case, append-only, each change also
 * written to the case audit trail in the same transaction.
 * <p>
 * PAYMENTS are idempotent on externalReference (a bank/gateway reference): recording the same reference again
 * returns the original entry, and the same reference against a different case is refused. The ledger is an
 * attribution record — recording the money against the invoice stays invoicing's job. An optional ptpId also files
 * a receipt on that promise so the PTP engine sees it; note that PTP receipts are append-only, so reversing such a
 * payment does not remove the receipt — the audit entry says so, and the promise should be reviewed by hand.
 * <p>
 * FEES need an APPROVED, in-date FeeRule (see FeeRule: nothing is seeded, each rule carries its legal basis and is
 * four-eyes approved) and are refused while the case is DISPUTED or terminal. Percentage fees use the live
 * outstanding per invoicing. Waiving fees is limited to what is currently owed in fees.
 */
@Service
@RequiredArgsConstructor
public class RecoveryLedgerService {

    private static final Set<CaseStatus> NO_FEES = EnumSet.of(CaseStatus.DISPUTED, CaseStatus.CLOSED,
            CaseStatus.WRITTEN_OFF, CaseStatus.SETTLED);

    public record LedgerSummary(BigDecimal paymentsGross, BigDecimal reversals, BigDecimal netRecovered,
                                BigDecimal principalRecoveredNet, BigDecimal feesRecoveredNet, BigDecimal feesCharged,
                                BigDecimal feesWaived, BigDecimal feesOutstanding, BigDecimal liveInvoicingOutstanding,
                                BigDecimal caseSnapshotOutstanding, int entryCount) {}

    private final RecoveryEntryRepository repository;
    private final FeeRuleRepository feeRuleRepository;
    private final DebtCollectionCaseService caseService;
    private final InvoicingFacade invoicingFacade;
    private final PtpService ptpService;
    private final CaseAuditService audit;
    private final LedgerProperties props;

    @Transactional
    public RecoveryEntry recordPayment(TenantId tenantId, UUID caseId, BigDecimal amount, LocalDate date,
                                       PaymentSource source, String externalReference, BigDecimal allocateToFees,
                                       UUID ptpId, String notes, UUID actorId, String actorName) {
        caseService.get(tenantId, caseId);
        String ref = externalReference == null || externalReference.isBlank() ? null : externalReference.trim();
        if (ref != null) {
            var existing = repository.findPaymentByExternalReference(tenantId, ref);
            if (existing.isPresent()) {
                if (!existing.get().getCaseId().equals(caseId)) {
                    throw new IllegalStateException("Payment reference " + ref + " is already recorded against a different case");
                }
                return existing.get();
            }
        }
        if (ptpId != null && !ptpService.get(tenantId, ptpId).getCaseId().equals(caseId)) {
            throw new IllegalArgumentException("The promise to pay does not belong to this case");
        }
        BigDecimal feesOwed = summarise(repository.findByCaseId(tenantId, caseId)).feesOutstanding();
        var split = PaymentAllocator.split(amount, feesOwed, props.isAllocateFeesFirst(), allocateToFees);
        RecoveryEntry e = repository.save(RecoveryEntry.payment(tenantId, caseId, amount, split.principal(), split.fees(),
                date, source, ref, ptpId, notes, actorId, actorName));
        audit.record(tenantId, caseId, "PAYMENT_RECORDED", "R" + amount + " via " + source
                + (ref != null ? " ref " + ref : "") + " (principal R" + split.principal() + ", fees R" + split.fees() + ")");
        if (ptpId != null) {
            ptpService.recordReceipt(tenantId, ptpId, amount, e.getEffectiveDate(), ref, notes, actorId, actorName);
        }
        return e;
    }

    @Transactional
    public RecoveryEntry reversePayment(TenantId tenantId, UUID entryId, String reason, LocalDate date, UUID actorId,
                                        String actorName) {
        RecoveryEntry original = repository.findByTenantIdAndId(tenantId, entryId)
                .orElseThrow(() -> new ResourceNotFoundException("RecoveryEntry", entryId.toString()));
        if (original.getEntryType() != LedgerEntryType.PAYMENT) {
            throw new IllegalArgumentException("Only a PAYMENT can be reversed");
        }
        if (repository.findReversalOf(tenantId, entryId).isPresent()) {
            throw new IllegalStateException("This payment has already been reversed");
        }
        RecoveryEntry rev = repository.save(RecoveryEntry.reversalOf(tenantId, original, date, reason, actorId, actorName));
        audit.record(tenantId, original.getCaseId(), "PAYMENT_REVERSED", "R" + original.getAmount() + " reversed: " + reason
                + (original.getPtpId() != null ? " — NOTE: this payment was filed as a receipt on promise "
                + original.getPtpId() + "; receipts are append-only, so review that promise manually" : ""));
        return rev;
    }

    @Transactional
    public RecoveryEntry chargeFee(TenantId tenantId, UUID caseId, String ruleCode, LocalDate date, String notes,
                                   UUID actorId, String actorName) {
        DebtCollectionCase c = caseService.get(tenantId, caseId);
        if (NO_FEES.contains(c.getStatus())) {
            throw new IllegalStateException("Fees cannot be charged while the case is " + c.getStatus());
        }
        String code = ruleCode == null ? "" : ruleCode.trim().toUpperCase();
        FeeRule rule = feeRuleRepository.findApprovedByCode(tenantId, code)
                .orElseThrow(() -> new ResourceNotFoundException("Approved fee rule", code));
        LocalDate d = date != null ? date : LocalDate.now();
        if (!rule.isUsableOn(d)) {
            throw new IllegalStateException("Fee rule " + code + " is not in effect on " + d
                    + " (effective " + rule.getEffectiveFrom() + (rule.getEffectiveTo() != null ? " to " + rule.getEffectiveTo() : "") + ")");
        }
        List<RecoveryEntry> entries = repository.findByCaseId(tenantId, caseId);
        List<RecoveryEntry> sameRule = entries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.FEE_CHARGE && code.equals(e.getFeeRuleCode())).toList();
        BigDecimal alreadyCharged = sameRule.stream().map(RecoveryEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long thisMonth = sameRule.stream().filter(e -> YearMonth.from(e.getEffectiveDate()).equals(YearMonth.from(d))).count();

        var result = FeeCalculator.compute(new FeeCalculator.Terms(rule.getCalcMethod(), rule.getFixedAmount(),
                rule.getPercentOfOutstanding(), rule.getMaxPerCase(), rule.getMaxChargesPerMonth()),
                liveOutstanding(tenantId, c), alreadyCharged, thisMonth);
        RecoveryEntry e = repository.save(RecoveryEntry.feeCharge(tenantId, caseId, result.amount(), rule.getCode(),
                rule.getRuleVersion(), d, notes, actorId, actorName));
        audit.record(tenantId, caseId, "FEE_CHARGED", "R" + result.amount() + " under " + rule.getCode() + " v"
                + rule.getRuleVersion() + (result.capped() ? " (trimmed to the per-case cap)" : ""));
        return e;
    }

    @Transactional
    public RecoveryEntry waiveFees(TenantId tenantId, UUID caseId, BigDecimal amount, LocalDate date, String reason,
                                   UUID actorId, String actorName) {
        caseService.get(tenantId, caseId);
        BigDecimal owed = summarise(repository.findByCaseId(tenantId, caseId)).feesOutstanding();
        if (amount != null && amount.compareTo(owed) > 0) {
            throw new IllegalArgumentException("Cannot waive more than the fees currently owed (R" + owed + ")");
        }
        RecoveryEntry e = repository.save(RecoveryEntry.feeWaiver(tenantId, caseId, amount, date, reason, actorId, actorName));
        audit.record(tenantId, caseId, "FEES_WAIVED", "R" + amount + " waived: " + reason);
        return e;
    }

    @Transactional(readOnly = true)
    public List<RecoveryEntry> list(TenantId tenantId, UUID caseId) {
        caseService.get(tenantId, caseId);
        return repository.findByCaseId(tenantId, caseId);
    }

    @Transactional(readOnly = true)
    public LedgerSummary summary(TenantId tenantId, UUID caseId) {
        DebtCollectionCase c = caseService.get(tenantId, caseId);
        List<RecoveryEntry> entries = repository.findByCaseId(tenantId, caseId);
        Totals t = summarise(entries);
        return new LedgerSummary(t.paymentsGross(), t.reversals(), t.netRecovered(), t.principalRecoveredNet(),
                t.feesRecoveredNet(), t.feesCharged(), t.feesWaived(), t.feesOutstanding(), liveOutstanding(tenantId, c),
                c.getTotalOutstanding(), entries.size());
    }

    static Totals summarise(List<RecoveryEntry> entries) {
        return LedgerMath.summarise(entries.stream()
                .map(e -> new Line(e.getEntryType(), e.getAmount(), e.getAllocatedPrincipal(), e.getAllocatedFees())).toList());
    }

    /** Live outstanding on the case's linked invoices; invoices no longer outstanding count as paid. */
    private BigDecimal liveOutstanding(TenantId tenantId, DebtCollectionCase c) {
        Map<UUID, OutstandingInvoiceSummary> byId = invoicingFacade.findOutstandingInvoices(tenantId).stream()
                .collect(Collectors.toMap(OutstandingInvoiceSummary::id, Function.identity(), (a, b) -> a));
        BigDecimal total = BigDecimal.ZERO;
        for (UUID id : c.getLinkedInvoiceIds()) {
            OutstandingInvoiceSummary inv = byId.get(id);
            if (inv == null) continue;
            BigDecimal paid = inv.settled();   // finance patch F8: paid plus credit notes
            total = total.add(inv.total().subtract(paid).max(BigDecimal.ZERO));
        }
        return total;
    }
}
