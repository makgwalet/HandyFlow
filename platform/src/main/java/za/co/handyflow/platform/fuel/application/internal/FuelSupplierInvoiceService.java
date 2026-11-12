package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.SupplierInvoiceDtos.*;
import za.co.handyflow.platform.ap.application.ApFacade;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Supplier invoices for fuel: capture against a purchase order and the receipts it covers, a three-way match, then approve or reject.
 * Approving something that does not match needs a reason. This does not post to accounts payable or pay anything.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelSupplierInvoiceService {

    private final FuelSupplierInvoiceRepository invoices;
    private final FuelSupplierInvoiceReceiptRepository links;
    private final FuelReceiptRepository receipts;
    private final FuelPurchaseOrderRepository orders;
    private final FuelSupplierRepository suppliers;
    private final ApFacade ap;
    private final FeatureGuard featureGuard;

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(TenantId tenantId, int limit) {
        List<FuelSupplierInvoice> list = invoices.findRecent(tenantId, PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
        if (list.isEmpty()) return List.of();
        Map<UUID, List<UUID>> receiptIdsByInvoice = new HashMap<>();
        Set<UUID> allReceipts = new HashSet<>();
        for (FuelSupplierInvoiceReceipt l : links.findByInvoices(tenantId, list.stream().map(FuelSupplierInvoice::getId).toList())) {
            receiptIdsByInvoice.computeIfAbsent(l.getInvoiceId(), k -> new ArrayList<>()).add(l.getReceiptId());
            allReceipts.add(l.getReceiptId());
        }
        Map<UUID, FuelReceipt> receiptMap = allReceipts.isEmpty() ? Map.of()
                : receipts.findActiveByIds(tenantId, allReceipts).stream().collect(Collectors.toMap(FuelReceipt::getId, Function.identity()));
        Map<UUID, FuelPurchaseOrder> poMap = orders.findAllActive(tenantId).stream().collect(Collectors.toMap(FuelPurchaseOrder::getId, Function.identity()));
        Map<UUID, String> names = suppliers.findAllActive(tenantId).stream().collect(Collectors.toMap(FuelSupplier::getId, FuelSupplier::getName));
        return list.stream().map(i -> build(i, receiptIdsByInvoice.getOrDefault(i.getId(), List.of()).stream().map(receiptMap::get).filter(Objects::nonNull).toList(),
                i.getPurchaseOrderId() == null ? null : poMap.get(i.getPurchaseOrderId()), names.getOrDefault(i.getSupplierId(), "Unknown supplier"))).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(TenantId tenantId, UUID id) {
        return view(tenantId, find(tenantId, id));
    }

    /** Receipts from a supplier that no invoice covers yet. */
    @Transactional(readOnly = true)
    public List<ReceiptLine> candidates(TenantId tenantId, UUID supplierId) {
        requireSupplier(tenantId, supplierId);
        return receipts.findInvoiceCandidates(tenantId, supplierId, PageRequest.of(0, 100)).stream().map(FuelSupplierInvoiceService::line).toList();
    }

    @Transactional
    public InvoiceResponse capture(TenantId tenantId, CaptureRequest req, UUID by) {
        requireSupplier(tenantId, req.supplierId());
        String number = req.invoiceNumber().trim();
        if (invoices.existsLive(tenantId, req.supplierId(), number)) {
            throw new IllegalStateException("This supplier's invoice " + number + " has already been captured.");
        }
        FuelPurchaseOrder po = null;
        if (req.purchaseOrderId() != null) {
            po = orders.findActiveById(tenantId, req.purchaseOrderId()).orElseThrow(() -> new ResourceNotFoundException("Purchase order", req.purchaseOrderId().toString()));
            if (!po.getSupplierId().equals(req.supplierId())) throw new IllegalArgumentException(po.getPoNumber() + " is for a different supplier.");
        }
        List<UUID> ids = req.receiptIds() == null ? List.of() : req.receiptIds().stream().distinct().toList();
        List<FuelReceipt> chosen = ids.isEmpty() ? List.of() : receipts.findActiveByIds(tenantId, ids);
        if (chosen.size() != ids.size()) throw new IllegalArgumentException("One of the chosen receipts was not found.");
        for (FuelReceipt r : chosen) {
            if (!req.supplierId().equals(r.getSupplierId())) throw new IllegalArgumentException("A chosen receipt is from a different supplier.");
            if (r.isReversed()) throw new IllegalArgumentException("A chosen receipt has been reversed and cannot be invoiced.");
            if (po != null && r.getPurchaseOrderId() != null && !r.getPurchaseOrderId().equals(po.getId())) {
                throw new IllegalArgumentException("A chosen receipt belongs to a different purchase order than " + po.getPoNumber() + ".");
            }
        }
        if (!ids.isEmpty() && !links.findAlreadyLinked(tenantId, ids).isEmpty()) {
            throw new IllegalStateException("A chosen receipt is already covered by another supplier invoice.");
        }
        FuelSupplierInvoice inv = FuelSupplierInvoice.capture(tenantId, req.supplierId(), po == null ? null : po.getId(), number, req.invoiceDate(), req.dueDate(),
                req.litres(), req.pricePerLitre(), req.otherCharges(), req.vatAmount(), req.notes(), by);
        inv.recordMatch(SupplierInvoiceMatcher.match(inv.getLitres(), inv.getPricePerLitre(), chosen, po).status());
        invoices.save(inv);
        for (FuelReceipt r : chosen) links.save(FuelSupplierInvoiceReceipt.of(tenantId, inv.getId(), r.getId()));
        log.info("Supplier invoice captured {} supplier={} match={} by={}", number, req.supplierId(), inv.getMatchStatus(), by);
        return build(inv, chosen, po, supplierName(tenantId, req.supplierId()));
    }

    @Transactional
    public InvoiceResponse approve(TenantId tenantId, UUID id, DecisionRequest req, UUID by) {
        FuelSupplierInvoice inv = find(tenantId, id);
        InvoiceResponse current = view(tenantId, inv); // match is recomputed now: a receipt reversed since capture counts
        inv.approve(current.matchStatus(), req == null ? null : req.reason(), by);
        // Same transaction: if AP refuses, the approval rolls back and nothing is half done. Tenants without AP keep approving as before.
        if (featureGuard.hasModule("ap")) handOffToAp(tenantId, inv, by);
        invoices.save(inv);
        log.info("Supplier invoice approved {} match={} by={}", inv.getInvoiceNumber(), current.matchStatus(), by);
        return view(tenantId, inv);
    }

    /** For invoices approved before AP was on, or before this existed: hands the approved invoice over now. Safe to repeat. */
    @Transactional
    public InvoiceResponse sendToAp(TenantId tenantId, UUID id, UUID by) {
        FuelSupplierInvoice inv = find(tenantId, id);
        if (!inv.isApproved()) throw new IllegalStateException("Only an approved invoice can be sent to Accounts Payable.");
        featureGuard.requireModule("ap");
        handOffToAp(tenantId, inv, by);
        invoices.save(inv);
        return view(tenantId, inv);
    }

    private void handOffToAp(TenantId tenantId, FuelSupplierInvoice inv, UUID by) {
        if (inv.isHandedToAp()) return;
        // Reference is the invoice id (unique per tenant); invoice numbers are only unique per supplier. The bill number is prefixed so it cannot collide with a hand-typed AP bill.
        String billNumber = ("FUEL-" + inv.getInvoiceNumber());
        if (billNumber.length() > 50) billNumber = billNumber.substring(0, 50);
        UUID billId = ap.createBillFromFuelInvoice(tenantId, supplierName(tenantId, inv.getSupplierId()), inv.getId().toString(), billNumber,
                inv.getInvoiceDate(), inv.getDueDate() == null ? inv.getInvoiceDate() : inv.getDueDate(), inv.amountExVat(), inv.getVatAmount(), by);
        inv.recordApHandOff(billId);
        log.info("Supplier invoice {} handed to AP bill={}", inv.getInvoiceNumber(), billId);
    }

    @Transactional
    public InvoiceResponse reject(TenantId tenantId, UUID id, DecisionRequest req, UUID by) {
        FuelSupplierInvoice inv = find(tenantId, id);
        inv.reject(req == null ? null : req.reason(), by);
        invoices.save(inv);
        // free the receipts so the corrected invoice can cover them
        List<FuelSupplierInvoiceReceipt> held = links.findByInvoices(tenantId, List.of(id));
        links.deleteAll(held);
        log.info("Supplier invoice rejected {} by={}", inv.getInvoiceNumber(), by);
        InvoiceResponse r = build(inv, List.of(), null, supplierName(tenantId, inv.getSupplierId()));
        return r;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private InvoiceResponse view(TenantId tenantId, FuelSupplierInvoice inv) {
        List<UUID> ids = links.findByInvoices(tenantId, List.of(inv.getId())).stream().map(FuelSupplierInvoiceReceipt::getReceiptId).toList();
        List<FuelReceipt> rs = ids.isEmpty() ? List.of() : receipts.findActiveByIds(tenantId, ids);
        FuelPurchaseOrder po = inv.getPurchaseOrderId() == null ? null : orders.findActiveById(tenantId, inv.getPurchaseOrderId()).orElse(null);
        return build(inv, rs, po, supplierName(tenantId, inv.getSupplierId()));
    }

    /** A rejected invoice is not matched any more; an approved one shows the match it was approved on. */
    private static InvoiceResponse build(FuelSupplierInvoice i, List<FuelReceipt> rs, FuelPurchaseOrder po, String supplierName) {
        SupplierInvoiceMatcher.Result m = SupplierInvoiceMatcher.match(i.getLitres(), i.getPricePerLitre(), rs, po);
        String matchStatus = i.isCaptured() ? m.status() : i.getMatchStatus();
        return new InvoiceResponse(i.getId(), i.getSupplierId(), supplierName, i.getPurchaseOrderId(), po == null ? null : po.getPoNumber(), i.getInvoiceNumber(),
                i.getInvoiceDate(), i.getDueDate(), i.getLitres(), i.getPricePerLitre(), i.getOtherCharges(), i.getVatAmount(), i.getTotalAmount(),
                i.getStatus(), matchStatus, i.getDecisionReason(), i.getNotes(), i.getCapturedAt(), i.getDecidedAt(),
                m.litresReceived(), m.receiptValue(), m.litresVariance(), m.goodsValue(), m.valueVariance(), m.priceVsPo(),
                "REJECTED".equals(i.getStatus()) ? List.of() : m.issues(), rs.stream().map(FuelSupplierInvoiceService::line).toList(),
                i.getApBillId(), i.getApHandedOffAt());
    }

    private static ReceiptLine line(FuelReceipt r) {
        return new ReceiptLine(r.getId(), r.getReceivedAt(), r.getLitresReceived(), r.getPricePerLitre(), r.getTotalCost(), r.getDeliveryNote(), r.isReversed());
    }

    private void requireSupplier(TenantId t, UUID id) {
        if (suppliers.findActiveById(t, id).isEmpty()) throw new ResourceNotFoundException("Supplier", id.toString());
    }

    private String supplierName(TenantId t, UUID id) { return suppliers.findActiveById(t, id).map(FuelSupplier::getName).orElse("Unknown supplier"); }

    private FuelSupplierInvoice find(TenantId t, UUID id) {
        return invoices.findByTenantAndId(t, id).orElseThrow(() -> new ResourceNotFoundException("Supplier invoice", id.toString()));
    }
}
