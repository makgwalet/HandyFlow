package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.ContactMethod;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.PromiseToPay;
import za.co.handyflow.platform.debtcollection.domain.model.PtpEvent;
import za.co.handyflow.platform.debtcollection.domain.model.PtpFrequency;
import za.co.handyflow.platform.debtcollection.domain.model.PtpReceipt;
import za.co.handyflow.platform.debtcollection.domain.model.PtpStatus;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemDisposition;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemType;
import za.co.handyflow.platform.debtcollection.domain.repository.DebtCollectionCaseRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.PromiseToPayRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.PtpEventRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.PtpReceiptRepository;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.OutstandingInvoiceSummary;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Promise-to-pay engine: reminder → payment check → kept / partially kept / broken → next step.
 * <p>
 * RECONCILIATION. Before a promise is ever marked broken, payment received is established as
 * max(detected, manual): detected = baselineOutstanding − current outstanding of the case's
 * linked invoices per InvoicingFacade (an invoice no longer in the outstanding list counts as
 * fully paid); manual = sum of PtpReceipts staff recorded for payments invoicing doesn't show yet.
 * max() — not sum — because staff may record the same EFT in both places; the trade-off is that
 * genuinely separate payments split across the two sources are under-counted until invoicing
 * catches up (a conservative error: it can only delay "kept", and reassess() fixes it).
 * <p>
 * OUTCOME RULES (PtpPolicy constants — a per-client ruleset arrives with the workflow chunk):
 * received ≥ amount (±R0.01) → KEPT, detected as soon as seen; once today > dueDate + graceDays:
 * received ≥ 50% of amount → PARTIALLY_KEPT, otherwise BROKEN. A partial or broken outcome raises
 * ONE BROKEN_PTP work item (sourceKey BROKEN_PTP:{id}), and deliberately does NOT change the case
 * status — what staff do next (re-contact, new PTP, legal review) is their call, as with plans.
 * <p>
 * LATE / CORRECTED DATA. A BROKEN promise can be re-assessed upward (manual /reconcile, or a new
 * receipt). Because the work item is keyed, there is never a second "broken" event; on full
 * recovery the open work item is cancelled. Recovery is recorded as RECOVERED_AFTER_BREAK.
 * <p>
 * SERIES. A recurring PTP spawns its next occurrence (anchored to the previous due date) when
 * the current one is KEPT. A partial or broken outcome ends the series.
 */
@Service
@RequiredArgsConstructor
public class PtpService {

    static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    static final BigDecimal PARTIAL_KEPT_MIN_FRACTION = new BigDecimal("0.50");
    static final int REMINDER_DAYS_BEFORE_DUE = 2;

    public record PtpMetrics(LocalDate from, LocalDate to, long resolvedCount, long keptCount, long partialCount,
                             long brokenCount, BigDecimal keptRateByCount, BigDecimal brokenRateByCount,
                             BigDecimal promisedValue, BigDecimal receivedValue, BigDecimal keptRateByValue,
                             BigDecimal avgDaysToDetectBreak, BigDecimal valueRecoveredOnBrokenPromises) {}

    private final PromiseToPayRepository repository;
    private final PtpReceiptRepository receiptRepository;
    private final PtpEventRepository eventRepository;
    private final DebtCollectionCaseRepository caseRepository;
    private final DebtCollectionCaseService caseService;
    private final InvoicingFacade invoicingFacade;
    private final WorkItemService workItemService;

    // ── commands ─────────────────────────────────────────────────────────────

    @Transactional
    public PromiseToPay propose(TenantId tenantId, UUID caseId, BigDecimal amount, LocalDate dueDate,
                                PtpFrequency frequency, int additionalOccurrences, ContactMethod source, String terms,
                                int graceDays, boolean accept, UUID workItemId, UUID actorId, String actorName) {
        DebtCollectionCase c = caseService.get(tenantId, caseId);
        if (c.getStatus() == CaseStatus.CLOSED) {
            throw new IllegalStateException("Cannot record a promise on a closed case");
        }
        if (dueDate != null && dueDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("A promise's due date cannot be in the past");
        }
        if (!repository.findOpenByCaseId(tenantId, caseId).isEmpty()) {
            throw new IllegalStateException("This case already has a proposed/active promise — cancel it (with a "
                    + "reason) before recording a new one, so the audit trail shows the replacement");
        }
        PromiseToPay p = repository.save(PromiseToPay.propose(tenantId, caseId, null, amount, dueDate, frequency,
                additionalOccurrences, source, terms, graceDays, actorId, actorName));
        audit(p, "PROPOSED", "R" + amount + " by " + dueDate + " via " + source, actorId, actorName);
        if (accept) {
            activate(p, c, actorId, actorName);
        }
        if (workItemId != null) {
            workItemService.complete(tenantId, workItemId, WorkItemDisposition.PTP_CAPTURED, "PTP " + p.getId(),
                    actorId, actorName);
        }
        return p;
    }

    @Transactional
    public PromiseToPay accept(TenantId tenantId, UUID id, UUID actorId, String actorName) {
        PromiseToPay p = find(tenantId, id);
        activate(p, caseService.get(tenantId, p.getCaseId()), actorId, actorName);
        return p;
    }

    @Transactional
    public PromiseToPay cancel(TenantId tenantId, UUID id, String reason, UUID actorId, String actorName) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to cancel a promise");
        }
        PromiseToPay p = find(tenantId, id);
        p.cancel(reason);
        audit(p, "CANCELLED", reason, actorId, actorName);
        workItemService.cancelBySourceKey(tenantId, dueKey(p), "Promise cancelled");
        return repository.save(p);
    }

    /** Records a payment invoicing doesn't show yet, then re-assesses immediately. */
    @Transactional
    public PromiseToPay recordReceipt(TenantId tenantId, UUID id, BigDecimal amount, LocalDate receivedDate,
                                      String reference, String notes, UUID actorId, String actorName) {
        PromiseToPay p = find(tenantId, id);
        if (p.getStatus() == PtpStatus.CANCELLED || p.getStatus() == PtpStatus.PROPOSED) {
            throw new IllegalStateException("Receipts can only be recorded against an ACTIVE or resolved promise");
        }
        receiptRepository.save(PtpReceipt.of(tenantId, id, p.getCaseId(), amount, receivedDate, reference, notes, actorId));
        audit(p, "RECEIPT_RECORDED", "R" + amount + (reference != null ? " ref " + reference : ""), actorId, actorName);
        assess(p, LocalDate.now(), actorId, actorName);
        return p;
    }

    /** Manual "re-check now" — also the path for late or corrected payment data on a BROKEN promise. */
    @Transactional
    public PromiseToPay reassess(TenantId tenantId, UUID id, UUID actorId, String actorName) {
        PromiseToPay p = find(tenantId, id);
        assess(p, LocalDate.now(), actorId, actorName);
        return p;
    }

    // ── scheduler-facing (system actor) ──────────────────────────────────────

    @Transactional
    public void evaluate(UUID ptpId, LocalDate today) {
        repository.findById(ptpId).ifPresent(p -> {
            if (p.getStatus() == PtpStatus.ACTIVE) assess(p, today, null, "system");
        });
    }

    /** Raises the PTP_DUE work item once the due date is within the reminder window. Idempotent. */
    @Transactional
    public void raiseReminder(UUID ptpId) {
        repository.findById(ptpId).ifPresent(p -> {
            if (p.getStatus() != PtpStatus.ACTIVE) return;
            workItemService.raise(p.getTenantId(), p.getCaseId(), WorkItemType.PTP_DUE,
                    "Promise of R" + p.getAmount() + " due " + p.getDueDate() + " — remind debtor / verify payment",
                    p.getDueDate(), dueKey(p), null, "system");
        });
    }

    // ── queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PromiseToPay> listForCase(TenantId tenantId, UUID caseId) {
        caseService.get(tenantId, caseId);
        return repository.findByCaseId(tenantId, caseId);
    }

    @Transactional(readOnly = true)
    public PromiseToPay get(TenantId tenantId, UUID id) {
        return find(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<PtpEvent> history(TenantId tenantId, UUID id) {
        find(tenantId, id);
        return eventRepository.findByPtpId(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<PtpReceipt> receipts(TenantId tenantId, UUID id) {
        find(tenantId, id);
        return receiptRepository.findByPtpId(tenantId, id);
    }

    /** Both by count and by value, as a PTP programme should be judged. Window is on resolved date. */
    @Transactional(readOnly = true)
    public PtpMetrics metrics(TenantId tenantId, LocalDate from, LocalDate to) {
        List<PromiseToPay> resolved = repository.findResolvedBetween(tenantId, from, to);
        long kept = count(resolved, PtpStatus.KEPT);
        long partial = count(resolved, PtpStatus.PARTIALLY_KEPT);
        long broken = count(resolved, PtpStatus.BROKEN);
        BigDecimal promised = resolved.stream().map(PromiseToPay::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = resolved.stream()
                .map(p -> p.getAmountReceived().min(p.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal brokenRecovered = resolved.stream().filter(p -> p.getStatus() == PtpStatus.BROKEN)
                .map(PromiseToPay::getAmountReceived).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgDays = broken == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(resolved.stream()
                .filter(p -> p.getStatus() == PtpStatus.BROKEN)
                .mapToLong(p -> ChronoUnit.DAYS.between(p.getDueDate(), p.getResolvedDate())).sum())
                .divide(BigDecimal.valueOf(broken), 1, RoundingMode.HALF_UP);
        return new PtpMetrics(from, to, resolved.size(), kept, partial, broken,
                ratio(kept, resolved.size()), ratio(broken, resolved.size()), promised, received,
                promised.signum() == 0 ? BigDecimal.ZERO
                        : received.divide(promised, 4, RoundingMode.HALF_UP), avgDays, brokenRecovered);
    }

    // ── core ─────────────────────────────────────────────────────────────────

    private void activate(PromiseToPay p, DebtCollectionCase c, UUID actorId, String actorName) {
        BigDecimal baseline = currentOutstanding(p.getTenantId(), c.getLinkedInvoiceIds());
        p.accept(baseline);
        repository.save(p);
        audit(p, "ACCEPTED", "baseline outstanding R" + baseline, actorId, actorName);
    }

    private void assess(PromiseToPay p, LocalDate today, UUID actorId, String actorName) {
        if (p.getStatus() == PtpStatus.CANCELLED || p.getStatus() == PtpStatus.PROPOSED) return;
        DebtCollectionCase c = caseRepository.findActiveById(p.getTenantId(), p.getCaseId()).orElse(null);
        if (c == null || c.getStatus() == CaseStatus.CLOSED) {
            if (p.getStatus() == PtpStatus.ACTIVE) {
                p.cancel("Case closed or removed");
                audit(p, "CANCELLED", "case closed or removed", actorId, actorName);
                workItemService.cancelBySourceKey(p.getTenantId(), dueKey(p), "Case closed or removed");
                repository.save(p);
            }
            return;
        }
        BigDecimal received = receivedFor(p, c);
        BigDecimal before = p.getAmountReceived();
        p.recordReceived(received);
        boolean full = received.compareTo(p.getAmount().subtract(TOLERANCE)) >= 0;
        boolean partial = received.compareTo(p.getAmount().multiply(PARTIAL_KEPT_MIN_FRACTION)) >= 0;
        boolean graceOver = today.isAfter(p.graceEnd());

        if (p.getStatus() == PtpStatus.ACTIVE) {
            if (full) {
                p.resolve(PtpStatus.KEPT, today, "received R" + received);
                audit(p, "KEPT", "received R" + received + " of R" + p.getAmount(), actorId, actorName);
                workItemService.cancelBySourceKey(p.getTenantId(), dueKey(p), "Promise kept");
                spawnNext(p, c);
            } else if (graceOver && partial) {
                p.resolve(PtpStatus.PARTIALLY_KEPT, today, "received R" + received + " of R" + p.getAmount());
                audit(p, "PARTIALLY_KEPT", "received R" + received + " of R" + p.getAmount() + " after grace", actorId, actorName);
                raiseBroken(p, "Promise only partly kept: R" + received + " of R" + p.getAmount() + " — shortfall R"
                        + p.getAmount().subtract(received));
            } else if (graceOver) {
                p.resolve(PtpStatus.BROKEN, today, "received R" + received + " of R" + p.getAmount());
                audit(p, "BROKEN", "received R" + received + " of R" + p.getAmount() + " after grace ended "
                        + p.graceEnd(), actorId, actorName);
                raiseBroken(p, "Promise of R" + p.getAmount() + " due " + p.getDueDate() + " BROKEN — received R" + received);
            }
        } else if (p.getStatus() == PtpStatus.BROKEN && (full || (partial && received.compareTo(before) > 0))) {
            PtpStatus up = full ? PtpStatus.KEPT : PtpStatus.PARTIALLY_KEPT;
            p.resolve(up, today, "recovered after break: received R" + received);
            audit(p, "RECOVERED_AFTER_BREAK", "now " + up + ", received R" + received, actorId, actorName);
            if (full) {
                workItemService.cancelBySourceKey(p.getTenantId(), brokenKey(p), "Payment received after break");
            }
        }
        repository.save(p);
    }

    private void spawnNext(PromiseToPay p, DebtCollectionCase c) {
        if (p.getFrequency() == PtpFrequency.ONE_OFF || p.getOccurrencesRemaining() <= 0) return;
        PromiseToPay next = PromiseToPay.propose(p.getTenantId(), p.getCaseId(), p.getSeriesId(), p.getAmount(),
                p.getFrequency().next(p.getDueDate()), p.getFrequency(), p.getOccurrencesRemaining() - 1,
                p.getSource(), p.getTerms(), p.getGraceDays(), p.getCreatedBy(), p.getCreatedByName());
        next = repository.save(next);
        next.accept(currentOutstanding(p.getTenantId(), c.getLinkedInvoiceIds()));
        repository.save(next);
        audit(next, "SERIES_NEXT", "spawned after " + p.getId() + ", due " + next.getDueDate(), null, "system");
    }

    private void raiseBroken(PromiseToPay p, String title) {
        try {
            workItemService.raise(p.getTenantId(), p.getCaseId(), WorkItemType.BROKEN_PTP, title, p.getResolvedDate(),
                    brokenKey(p), null, "system");
        } catch (Exception e) {
            // case closed mid-flight etc. — the PTP outcome itself must still stand
        }
    }

    private BigDecimal receivedFor(PromiseToPay p, DebtCollectionCase c) {
        BigDecimal detected = p.getBaselineOutstanding() == null ? BigDecimal.ZERO
                : p.getBaselineOutstanding().subtract(currentOutstanding(p.getTenantId(), c.getLinkedInvoiceIds())).max(BigDecimal.ZERO);
        BigDecimal manual = receiptRepository.findByPtpId(p.getTenantId(), p.getId()).stream()
                .map(PtpReceipt::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return detected.max(manual);
    }

    /** Sum of outstanding across the linked invoices; an invoice absent from invoicing's outstanding list is fully paid (0). */
    private BigDecimal currentOutstanding(TenantId tenantId, Set<UUID> invoiceIds) {
        Map<UUID, OutstandingInvoiceSummary> byId = invoicingFacade.findOutstandingInvoices(tenantId).stream()
                .collect(Collectors.toMap(OutstandingInvoiceSummary::id, Function.identity(), (a, b) -> a));
        BigDecimal total = BigDecimal.ZERO;
        for (UUID id : invoiceIds) {
            OutstandingInvoiceSummary inv = byId.get(id);
            if (inv == null) continue;
            BigDecimal paid = inv.amountPaid() != null ? inv.amountPaid() : BigDecimal.ZERO;
            total = total.add(inv.total().subtract(paid));
        }
        return total;
    }

    private PromiseToPay find(TenantId tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("PromiseToPay", id.toString()));
    }

    private void audit(PromiseToPay p, String type, String detail, UUID actorId, String actorName) {
        eventRepository.save(PtpEvent.of(p.getTenantId(), p.getId(), p.getCaseId(), type, detail, actorId, actorName));
    }

    private static String dueKey(PromiseToPay p) { return "PTP_DUE:" + p.getId() + ":" + p.getDueDate(); }

    private static String brokenKey(PromiseToPay p) { return "BROKEN_PTP:" + p.getId(); }

    private static long count(List<PromiseToPay> l, PtpStatus s) {
        return l.stream().filter(p -> p.getStatus() == s).count();
    }

    private static BigDecimal ratio(long n, long d) {
        return d == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(n).divide(BigDecimal.valueOf(d), 4, RoundingMode.HALF_UP);
    }
}
