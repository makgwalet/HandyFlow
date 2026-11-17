package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.ScmMatchTolerances.Tolerances;
import za.co.handyflow.platform.supplychain.application.internal.ThreeWayMatchEngine.Finding;
import za.co.handyflow.platform.supplychain.application.internal.ThreeWayMatchEngine.Outcome;
import za.co.handyflow.platform.supplychain.domain.enums.InvoiceStatus;
import za.co.handyflow.platform.supplychain.domain.enums.MatchExceptionType;
import za.co.handyflow.platform.supplychain.domain.enums.MatchStatus;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.supplychain.dto.MatchExceptionResponse;
import za.co.handyflow.platform.supplychain.dto.SupplierInvoiceLineResponse;

import java.math.BigDecimal;
import java.util.*;

/**
 * Gathers the data, runs {@link ThreeWayMatchEngine}, persists every finding as a
 * visible {@link ScMatchException}, and keeps the invoice's status consistent with the
 * outcome. Also used for manual re-runs (e.g. after the missing GRN is posted).
 * <p>
 * Behaviour change vs the old ScmService.performThreeWayMatch(): an unexpected error no
 * longer silently yields PENDING (which could still be approved). It yields
 * PARTIAL_MATCH with an ENGINE_ERROR exception — held until a re-match succeeds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceMatchingService {

    private final ScPurchaseOrderRepository poRepo;
    private final ScPoLineRepository poLineRepo;
    private final ScGoodsReceiptRepository grRepo;
    private final ScGrLineRepository grLineRepo;
    private final ScSupplierInvoiceRepository invoiceRepo;
    private final ScSupplierInvoiceLineRepository lineRepo;
    private final ScMatchExceptionRepository exceptionRepo;
    private final ScSupplierRepository supplierRepo;
    private final ThreeWayMatchEngine engine;
    private final ScmMatchTolerances tolerances;
    private final ScmNotificationService notificationService;
    private final ControlExceptionFacade controlExceptionFacade;

    /** The invoice (and its lines) must already be saved — exceptions reference it. */
    @Transactional
    public ScSupplierInvoice runMatch(TenantId tenantId, ScSupplierInvoice invoice) {
        UUID tid = tenantId.getValue();
        MatchStatus before = invoice.getMatchStatus();
        Tolerances tol = tolerances.forTenant(tid);

        Outcome outcome;
        try {
            outcome = evaluate(tid, invoice, tol);
        } catch (Exception e) {
            log.error("[SCM] Match evaluation failed for invoice={}: {}", invoice.getInvoiceNumber(), e.getMessage(), e);
            List<Finding> f = List.of(new Finding(MatchExceptionType.ENGINE_ERROR, null, null,
                    "Match evaluation failed: " + e.getMessage(), null, null));
            outcome = engine.toOutcome(f, MatchStatus.PARTIAL_MATCH, "");
        }

        exceptionRepo.findOpenForInvoice(tid, invoice.getId()).forEach(ScMatchException::supersede);
        for (Finding f : outcome.findings()) {
            exceptionRepo.save(ScMatchException.raise(tid, invoice.getId(), f.type(), f.poLineId(),
                    f.invoiceLineId(), f.message(), f.expected(), f.actual()));
        }

        invoice.applyMatchOutcome(outcome.status(), outcome.summary());
        ScSupplierInvoice saved = invoiceRepo.save(invoice);
        log.info("[SCM] Invoice {} matched: {} ({} finding(s))",
                saved.getInvoiceNumber(), outcome.status(), outcome.findings().size());

        // Notify / raise the cross-module control exception only on the TRANSITION into
        // dispute, so a re-run on an already-disputed invoice doesn't spam admins.
        if (outcome.status() == MatchStatus.DISPUTE && before != MatchStatus.DISPUTE) {
            String supplierName = supplierRepo.findByTenantIdAndId(tid, saved.getSupplierId())
                    .map(ScSupplier::getName).orElse("Unknown supplier");
            notificationService.notifyInvoiceDisputed(tid, saved.getInvoiceNumber(), supplierName, saved.getMatchNotes());
            controlExceptionFacade.raise(tenantId, "supplychain", "THREE_WAY_MATCH_DISPUTE",
                    "ScSupplierInvoice", saved.getId(), "WARNING",
                    "Invoice " + saved.getInvoiceNumber() + " from " + supplierName + " — " + saved.getMatchNotes());
        } else if (outcome.status() != MatchStatus.DISPUTE && before == MatchStatus.DISPUTE) {
            controlExceptionFacade.resolveForEntity(tenantId, "ScSupplierInvoice", saved.getId(),
                    null, "System", "Re-match cleared the dispute: " + saved.getMatchNotes());
        }
        return saved;
    }

    @Transactional
    public ScSupplierInvoice rematch(TenantId tenantId, UUID invoiceId) {
        ScSupplierInvoice inv = invoiceRepo.findByTenantIdAndId(tenantId.getValue(), invoiceId)
                .orElseThrow(() -> new HandyFlowException("Invoice not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (inv.getStatus() == InvoiceStatus.APPROVED || inv.getStatus() == InvoiceStatus.PAID
                || inv.getStatus() == InvoiceStatus.CANCELLED) {
            throw new HandyFlowException("An invoice that is " + inv.getStatus() + " can no longer be re-matched",
                    HttpStatus.CONFLICT, "INVOICE_LOCKED");
        }
        return runMatch(tenantId, inv);
    }

    @Transactional(readOnly = true)
    public List<MatchExceptionResponse> listExceptions(TenantId tenantId, UUID invoiceId) {
        requireInvoice(tenantId, invoiceId);
        return exceptionRepo.findAllForInvoice(tenantId.getValue(), invoiceId).stream()
                .map(e -> new MatchExceptionResponse(e.getId(), e.getExceptionType(),
                        e.getExceptionType().resultingStatus(), e.getPoLineId(), e.getInvoiceLineId(),
                        e.getMessage(), e.getExpectedValue(), e.getActualValue(), e.getStatus(),
                        e.getCreatedAt(), e.getResolvedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SupplierInvoiceLineResponse> listLines(TenantId tenantId, UUID invoiceId) {
        requireInvoice(tenantId, invoiceId);
        return lineRepo.findByInvoice(tenantId.getValue(), invoiceId).stream()
                .map(l -> new SupplierInvoiceLineResponse(l.getId(), l.getLineNumber(), l.getPoLineId(),
                        l.getItemName(), l.getQuantity(), l.getUnitPrice(), l.getVatRate(),
                        l.getLineTotal(), l.getVatAmount()))
                .toList();
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private Outcome evaluate(UUID tid, ScSupplierInvoice invoice, Tolerances tol) {
        long dupes = invoiceRepo.countSameAmountAndDate(tid, invoice.getSupplierId(), invoice.getTotalAmount(),
                invoice.getInvoiceDate(), invoice.getId(), InvoiceStatus.CANCELLED);

        if (invoice.getPurchaseOrderId() == null) {
            List<Finding> findings = new ArrayList<>();
            if (tol.requirePo()) {
                findings.add(new Finding(MatchExceptionType.NO_PURCHASE_ORDER, null, null,
                        "No purchase order is linked and this tenant requires one", null, null));
            }
            if (dupes > 0) {
                findings.add(new Finding(MatchExceptionType.POSSIBLE_DUPLICATE, null, null,
                        dupes + " other invoice(s) from this supplier have the same total and invoice date",
                        null, BigDecimal.valueOf(dupes)));
            }
            return engine.toOutcome(findings, MatchStatus.PENDING, "No PO linked — non-PO invoice, not matched");
        }

        // Row lock on the PO serialises concurrent invoices against the same PO lines.
        ScPurchaseOrder po = poRepo.lockByTenantIdAndId(tid, invoice.getPurchaseOrderId())
                .orElseThrow(() -> new IllegalStateException("Linked PO not found: " + invoice.getPurchaseOrderId()));

        List<ScPoLine> poLines = poLineRepo.findByPurchaseOrderId(po.getId());
        List<ScSupplierInvoiceLine> lines = lineRepo.findByInvoice(tid, invoice.getId());

        Boolean grPosted = null;
        if (invoice.getGoodsReceiptId() != null) {
            grPosted = grRepo.findByTenantIdAndId(tid, invoice.getGoodsReceiptId())
                    .map(gr -> "POSTED".equals(gr.getStatus()) && gr.getPurchaseOrderId().equals(po.getId()))
                    .orElse(false);
        }

        return engine.evaluate(new ThreeWayMatchEngine.Input(
                invoice, lines, po, poLines,
                toMap(grLineRepo.sumAcceptedByPoLine(tid, po.getId())),
                toMap(lineRepo.sumPriorInvoicedByPoLine(tid, po.getId(), invoice.getId(), InvoiceStatus.CANCELLED)),
                invoiceRepo.sumInvoicedForPo(tid, po.getId(), invoice.getId(), InvoiceStatus.CANCELLED),
                grPosted, dupes, tol));
    }

    private static Map<UUID, BigDecimal> toMap(List<Object[]> rows) {
        Map<UUID, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) m.put((UUID) r[0], (BigDecimal) r[1]);
        return m;
    }

    private void requireInvoice(TenantId tenantId, UUID invoiceId) {
        invoiceRepo.findByTenantIdAndId(tenantId.getValue(), invoiceId)
                .orElseThrow(() -> new HandyFlowException("Invoice not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }
}
