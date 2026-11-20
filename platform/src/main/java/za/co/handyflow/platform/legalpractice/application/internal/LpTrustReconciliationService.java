package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustReconItem;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustReconciliation;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustTransaction;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Trust account reconciliation: the bank statement against the trust ledger at a date, with the items that explain any
 * difference. A different person approves it, and approval locks the period (the database refuses later postings dated inside it).
 */
@Service
@RequiredArgsConstructor
public class LpTrustReconciliationService {

    private static final LocalDate ALL_TIME = LocalDate.of(2999, 12, 31);

    private final LpTrustReconciliationRepository reconRepo;
    private final LpTrustReconItemRepository itemRepo;
    private final LpTrustLedgerReadRepository ledgerRepo;
    private final LpTrustTransactionRepository trustRepo;
    private final LpTrustSettingsService settings;

    @Transactional(readOnly = true)
    public Page<TrustReconciliationSummary> list(TenantId tenantId, Pageable pageable) {
        return reconRepo.list(tenantId, pageable).map(r -> new TrustReconciliationSummary(r.getId(), r.getPeriodEnd(), r.getStatus(), r.getStatementBalance(),
                r.getDifference(), r.getPreparedByName(), r.getPreparedAt(), r.getApprovedByName(), r.getApprovedAt()));
    }

    @Transactional(readOnly = true)
    public TrustReconciliationResponse get(TenantId tenantId, UUID id) {
        return toResponse(tenantId, find(tenantId, id));
    }

    @Transactional(readOnly = true)
    public TrustLockResponse lock(TenantId tenantId) {
        return new TrustLockResponse(lockedThrough(tenantId));
    }

    @Transactional
    public TrustReconciliationResponse start(TenantId tenantId, StartReconciliationRequest req, UUID by, String byName) {
        if (req.periodEnd().isAfter(LocalDate.now())) throw new IllegalArgumentException("A reconciliation cannot be dated in the future");
        LocalDate locked = lockedThrough(tenantId);
        if (locked != null && !req.periodEnd().isAfter(locked)) throw new IllegalStateException("The trust account is already reconciled up to " + locked);
        List<LpTrustReconciliation> open = reconRepo.findOpen(tenantId);
        if (!open.isEmpty()) throw new IllegalStateException("The reconciliation to " + open.get(0).getPeriodEnd() + " is still open. Finish it or have it returned first.");
        LpTrustReconciliation r = LpTrustReconciliation.start(tenantId, req.periodEnd(), req.statementBalance(), blankToNull(req.note()), by, byName);
        reconRepo.save(r);
        return toResponse(tenantId, r);
    }

    @Transactional
    public TrustReconciliationResponse updateStatement(TenantId tenantId, UUID id, UpdateStatementRequest req) {
        LpTrustReconciliation r = find(tenantId, id);
        r.updateStatement(req.statementBalance(), blankToNull(req.note()));
        reconRepo.save(r);
        return toResponse(tenantId, r);
    }

    @Transactional
    public TrustReconciliationResponse addItem(TenantId tenantId, UUID id, AddReconItemRequest req, UUID by) {
        LpTrustReconciliation r = find(tenantId, id);
        requireDraft(r);
        if (req.itemDate().isAfter(r.getPeriodEnd())) throw new IllegalArgumentException("The item must be dated on or before " + r.getPeriodEnd());
        if (req.trustTransactionId() != null) {
            LpTrustTransaction tx = trustRepo.findActiveById(tenantId, req.trustTransactionId())
                    .orElseThrow(() -> new ResourceNotFoundException("LpTrustTransaction", req.trustTransactionId().toString()));
            if (tx.getTransactionDate().isAfter(r.getPeriodEnd())) throw new IllegalArgumentException("That ledger entry is dated after the reconciliation date");
            boolean deposit = "OUTSTANDING_DEPOSIT".equals(req.kind());
            if (deposit != tx.increasesTrustBalance()) throw new IllegalArgumentException(deposit ? "A deposit must be linked to a receipt" : "A payment must be linked to a payment, transfer or refund");
            if (tx.getAmount().compareTo(req.amount()) != 0) throw new IllegalArgumentException("The amount must match the ledger entry (" + tx.getAmount().toPlainString() + ")");
            for (LpTrustReconItem other : itemRepo.findForReconciliation(tenantId, r.getId())) {
                if (req.trustTransactionId().equals(other.getTrustTransactionId())) throw new IllegalStateException("That ledger entry is already listed");
            }
        }
        itemRepo.save(LpTrustReconItem.create(tenantId, r.getId(), req.kind(), req.amount(), req.itemDate(), req.description(), req.reference(), req.trustTransactionId(), by));
        return toResponse(tenantId, r);
    }

    @Transactional
    public TrustReconciliationResponse removeItem(TenantId tenantId, UUID id, UUID itemId) {
        LpTrustReconciliation r = find(tenantId, id);
        requireDraft(r);
        LpTrustReconItem item = itemRepo.findActiveById(tenantId, itemId).filter(i -> i.getReconciliationId().equals(r.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("LpTrustReconItem", itemId.toString()));
        itemRepo.delete(item);
        return toResponse(tenantId, r);
    }

    @Transactional
    public TrustReconciliationResponse submit(TenantId tenantId, UUID id, UUID by) {
        LpTrustReconciliation r = find(tenantId, id);
        requireDraft(r);
        Live live = live(tenantId, r);
        ReconciliationFigures f = live.figures;
        r.submit(f.cashBookBalance(), f.clientLedgerTotal(), f.outstandingDeposits(), f.outstandingPayments(), f.adjustedBankBalance(), f.difference(), live.mismatches.size(), by);
        reconRepo.save(r);
        return toResponse(tenantId, r);
    }

    @Transactional
    public TrustReconciliationResponse returnToDraft(TenantId tenantId, UUID id, String reason, String byName) {
        LpTrustReconciliation r = find(tenantId, id);
        r.returnToDraft(reason, byName);
        reconRepo.save(r);
        return toResponse(tenantId, r);
    }

    /** Approves and locks the period. The ledger is checked again: if it moved since the reconciliation was submitted, it must be returned. */
    @Transactional
    public TrustReconciliationResponse approve(TenantId tenantId, UUID id, ApproveReconciliationRequest req, UUID by, String byName) {
        LpTrustReconciliation r = find(tenantId, id);
        if (!"SUBMITTED".equals(r.getStatus())) throw new IllegalStateException("Only a submitted reconciliation can be approved");
        Live live = live(tenantId, r);
        if (changed(r, live.figures)) throw new IllegalStateException("The trust ledger has changed since this was submitted. Return it and submit it again.");
        r.approve(by, byName, req == null ? null : req.note(), settings.selfApprovalAllowed(tenantId));
        reconRepo.save(r);
        return toResponse(tenantId, r);
    }

    // ── figures ─────────────────────────────────────────────────────────────

    private record Live(ReconciliationFigures figures, List<LpTrustReconItem> items, List<TrustReconciliationResponse.ClientAmount> negatives,
                        List<TrustReconciliationResponse.Mismatch> mismatches) {}

    private Live live(TenantId tenantId, LpTrustReconciliation r) {
        List<LpTrustReconItem> items = itemRepo.findForReconciliation(tenantId, r.getId());
        BigDecimal deposits = BigDecimal.ZERO, payments = BigDecimal.ZERO;
        for (LpTrustReconItem i : items) {
            if ("OUTSTANDING_DEPOSIT".equals(i.getKind())) deposits = deposits.add(i.getAmount()); else payments = payments.add(i.getAmount());
        }
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, BigDecimal> stored = new LinkedHashMap<>();
        for (Object[] row : ledgerRepo.storedClientBalances(tenantId)) {
            names.put((UUID) row[0], (String) row[1]);
            stored.put((UUID) row[0], row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2]);
        }
        BigDecimal cashBook = ledgerRepo.cashBookAsAt(tenantId, r.getPeriodEnd());
        BigDecimal clientTotal = BigDecimal.ZERO;
        List<TrustReconciliationResponse.ClientAmount> negatives = new ArrayList<>();
        for (Object[] row : ledgerRepo.clientBalancesAsAt(tenantId, r.getPeriodEnd())) {
            BigDecimal bal = (BigDecimal) row[1];
            clientTotal = clientTotal.add(bal);
            if (bal.signum() < 0) negatives.add(new TrustReconciliationResponse.ClientAmount((UUID) row[0], names.getOrDefault((UUID) row[0], "Unknown client"), bal));
        }
        Map<UUID, BigDecimal> now = new HashMap<>();
        for (Object[] row : ledgerRepo.clientBalancesAsAt(tenantId, ALL_TIME)) now.put((UUID) row[0], (BigDecimal) row[1]);
        Set<UUID> clients = new LinkedHashSet<>(stored.keySet());
        clients.addAll(now.keySet());
        List<TrustReconciliationResponse.Mismatch> mismatches = new ArrayList<>();
        for (UUID c : clients) {
            BigDecimal s = stored.getOrDefault(c, BigDecimal.ZERO), l = now.getOrDefault(c, BigDecimal.ZERO);
            if (s.compareTo(l) != 0) mismatches.add(new TrustReconciliationResponse.Mismatch(c, names.getOrDefault(c, "Unknown client"), s, l));
        }
        return new Live(ReconciliationFigures.of(r.getStatementBalance(), deposits, payments, cashBook, clientTotal), items, negatives, mismatches);
    }

    private static boolean changed(LpTrustReconciliation r, ReconciliationFigures f) {
        return r.getCashBookBalance().compareTo(f.cashBookBalance()) != 0 || r.getClientLedgerTotal().compareTo(f.clientLedgerTotal()) != 0
                || r.getOutstandingDeposits().compareTo(f.outstandingDeposits()) != 0 || r.getOutstandingPayments().compareTo(f.outstandingPayments()) != 0
                || r.getDifference().compareTo(f.difference()) != 0;
    }

    private TrustReconciliationResponse toResponse(TenantId tenantId, LpTrustReconciliation r) {
        List<TrustReconciliationResponse.Item> noItems = List.of();
        if ("APPROVED".equals(r.getStatus())) {
            List<TrustReconciliationResponse.Item> items = itemRepo.findForReconciliation(tenantId, r.getId()).stream().map(LpTrustReconciliationService::item).toList();
            return build(r, r.getCashBookBalance(), r.getClientLedgerTotal(), r.getOutstandingDeposits(), r.getOutstandingPayments(), r.getAdjustedBankBalance(),
                    r.getDifference(), true, false, false, r.getLedgerMismatches() == null ? 0 : r.getLedgerMismatches(), items, List.of(), List.of());
        }
        Live l = live(tenantId, r);
        ReconciliationFigures f = l.figures;
        boolean changed = "SUBMITTED".equals(r.getStatus()) && changed(r, f);
        return build(r, f.cashBookBalance(), f.clientLedgerTotal(), f.outstandingDeposits(), f.outstandingPayments(), f.adjustedBankBalance(), f.difference(),
                f.balanced(), true, changed, l.mismatches.size(), l.items.stream().map(LpTrustReconciliationService::item).toList(), l.negatives, l.mismatches);
    }

    private static TrustReconciliationResponse.Item item(LpTrustReconItem i) {
        return new TrustReconciliationResponse.Item(i.getId(), i.getKind(), i.getAmount(), i.getItemDate(), i.getDescription(), i.getReference(), i.getTrustTransactionId());
    }

    private static TrustReconciliationResponse build(LpTrustReconciliation r, BigDecimal cashBook, BigDecimal clientTotal, BigDecimal deposits, BigDecimal payments,
                                                     BigDecimal adjusted, BigDecimal difference, boolean balanced, boolean live, boolean changed, int mismatchCount,
                                                     List<TrustReconciliationResponse.Item> items, List<TrustReconciliationResponse.ClientAmount> negatives,
                                                     List<TrustReconciliationResponse.Mismatch> mismatches) {
        return new TrustReconciliationResponse(r.getId(), r.getPeriodEnd(), r.getStatus(), r.getStatementBalance(), r.getStatementNote(), cashBook, clientTotal, deposits, payments,
                adjusted, difference, balanced, live, changed, mismatchCount, items, negatives, mismatches, r.getPreparedByName(), r.getPreparedAt(), r.getSubmittedAt(),
                r.getReturnReason(), r.getReturnedByName(), r.getApprovedByName(), r.getApprovedAt(), r.getApprovalNote(), r.isSelfApproved());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private LocalDate lockedThrough(TenantId tenantId) {
        List<LpTrustReconciliation> approved = reconRepo.findApproved(tenantId);
        return approved.isEmpty() ? null : approved.get(0).getPeriodEnd();
    }

    private LpTrustReconciliation find(TenantId tenantId, UUID id) {
        return reconRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("LpTrustReconciliation", id.toString()));
    }

    private static void requireDraft(LpTrustReconciliation r) {
        if (!"DRAFT".equals(r.getStatus())) throw new IllegalStateException("Items can only be changed while the reconciliation is a draft");
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
