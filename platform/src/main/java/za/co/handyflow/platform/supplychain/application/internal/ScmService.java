package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.approvals.dto.ApprovalStepResponse;
import za.co.handyflow.platform.ap.application.ApFacade;
import za.co.handyflow.platform.ap.domain.model.ApBill;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;
import za.co.handyflow.platform.shared.VatRateProvider;
import za.co.handyflow.platform.supplychain.domain.enums.*;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.supplychain.dto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmService {

    private final ScmSeparationOfDuties sod;
    private final PriceOverrideGate priceOverrideGate;
    private final ContractService contractService;
    private final ScSupplierRepository        supplierRepo;
    private final ScStockLocationRepository   locationRepo;
    private final ScInventoryRepository       inventoryRepo;
    private final ScStockMovementRepository   movementRepo;
    private final ScPurchaseOrderRepository   poRepo;
    private final ScGoodsReceiptRepository    grRepo;
    private final ScGrLineRepository          grLineRepo;
    private final ScSupplierInvoiceRepository invoiceRepo;
    // NEW (chunk 1): invoice lines + line-level matching.
    private final ScSupplierInvoiceLineRepository invoiceLineRepo;
    private final InvoiceMatchingService      matchingService;
    // NEW (chunk 2): controlled goods receiving.
    private final GoodsReceivingService       receivingService;
    // NEW (chunk 3): PO revision history.
    private final PoVersionService              poVersionService;
    // NEW (chunk 4): supplier audit trail.
    private final SupplierAuditService          supplierAudit;
    // NEW (chunk 7): budget check + commitment when a PO is approved.
    private final BudgetService                 budgetService;
    private final ScSupplierInvoiceAttachmentRepository attachmentRepo;
    private final ScPoLineRepository          poLineRepo;
    private final ApFacade apFacade;
    private final ScSupplierItemRepository    supplierItemRepo;
    private final ScmNotificationService      notificationService;
    private final JdbcTemplate                jdbc;
    private final ScPoPdfGenerator            poPdfGenerator;
    private final ScGrnPdfGenerator           grnPdfGenerator;
    private final ScRemittanceAdvicePdfGenerator remittanceAdvicePdfGenerator;

    private final TenantSequenceService       sequenceService;
    private final za.co.handyflow.platform.controls.application.ControlExceptionFacade controlExceptionFacade;

    private final ApprovalFacade approvalFacade;
    private final VatRateProvider vatRateProvider;


    /** app.scm.supplier.require-approval=true -> new suppliers start PENDING_APPROVAL and cannot be used until approved. */
    @Value("${app.scm.supplier.require-approval:false}")
    private boolean requireSupplierApproval;

    private static final String PO_APPROVALS_MODULE = "supplychain";
    private static final String PO_APPROVALS_ENTITY_TYPE = "PURCHASE_ORDER";

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ScmSummaryResponse getSummary(TenantId tenantId) {
        UUID tid = tenantId.getValue();
        long totalSuppliers   = supplierRepo.countByTenantIdAndStatus(tid, SupplierStatus.ACTIVE);
        long openPOs          = poRepo.countByTenantIdAndStatusIn(tid,
                List.of(PoStatus.APPROVED, PoStatus.SENT, PoStatus.ACKNOWLEDGED, PoStatus.PARTIALLY_RECEIVED));
        long pendingInvoices  = invoiceRepo.countByTenantIdAndStatus(tid, InvoiceStatus.RECEIVED);
        long approvalInvoices = invoiceRepo.countByTenantIdAndStatus(tid, InvoiceStatus.APPROVED);
        long lowStockItems    = inventoryRepo.countLowStock(tid);
        long overdueInvoices  = invoiceRepo.countOverdue(tid);
        return new ScmSummaryResponse(totalSuppliers, openPOs, pendingInvoices,
                approvalInvoices, lowStockItems, overdueInvoices);
    }

    // ── Suppliers ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScSupplier> getSuppliers(TenantId tenantId, String search, String status, Pageable pageable) {
        UUID tid = tenantId.getValue();
        if (search != null && !search.isBlank())
            return supplierRepo.search(tid, search, pageable);
        if (status != null && !status.isBlank()) {
            SupplierStatus statusEnum = SupplierStatus.valueOf(status.toUpperCase());
            return supplierRepo.findByTenantIdAndStatus(tid, statusEnum, pageable);
        }
        return supplierRepo.findByTenantId(tid, pageable);
    }

    @Transactional(readOnly = true)
    public ScSupplier getSupplier(TenantId tenantId, UUID id) {
        return supplierRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Supplier not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    @Transactional
    public ScSupplier createSupplier(TenantId tenantId, UUID userId, CreateSupplierRequest req) {
        // CHANGED (chunk 4): bank details validated and normalised (digits only) at the door, so
        // duplicate-account detection and later comparisons are reliable.
        SupplierBankRules.Bank bank = SupplierBankRules.validateOptional(req.bankAccount(), req.bankBranchCode());
        ScSupplier s = ScSupplier.create(
                tenantId.getValue(), req.name(), req.registrationNumber(), req.vatNumber(),
                req.bbbeeLevel(), req.bbbeeExpiry(), req.contactName(),
                req.contactEmail(), req.contactPhone(), req.website(),
                req.street(), req.suburb(), req.city(), req.province(), req.postalCode(),
                req.bankName(), bank.account(), bank.branch(),
                req.paymentTermsDays() != null ? req.paymentTermsDays() : 30,
                req.currency(), req.notes(), userId);
        if (requireSupplierApproval) s.requireApproval();
        ScSupplier saved = supplierRepo.save(s);
        supplierAudit.record(tenantId.getValue(), saved.getId(), "CREATED", null, saved.getStatus().name(),
                "Supplier registered", userId, TenantContext.getCurrentUserName());
        if (bank.account() != null
                && supplierRepo.countOtherWithBankAccount(tenantId.getValue(), bank.account(), saved.getId()) > 0) {
            controlExceptionFacade.raise(tenantId, "supplychain", "SUPPLIER_DUPLICATE_BANK_ACCOUNT",
                    "ScSupplier", saved.getId(), "CRITICAL",
                    "Supplier " + saved.getName() + " was registered with a bank account already used by another supplier");
        }
        return saved;
    }

    @Transactional
    public ScSupplier updateSupplier(TenantId tenantId, UUID id, UpdateSupplierRequest req) {
        ScSupplier s = getSupplier(tenantId, id);
        // CHANGED (chunk 4): status is no longer editable here. It used to be settable by any
        // SCM_ADMIN with no reason or audit — including flipping BLACKLISTED back to ACTIVE.
        if (req.status() != null && !req.status().equalsIgnoreCase(s.getStatus().name())) {
            throw new HandyFlowException("Supplier status can only be changed with the lifecycle actions "
                    + "(approve / suspend / block / reinstate / deactivate), which require a reason and are audited",
                    HttpStatus.BAD_REQUEST, "USE_LIFECYCLE_ENDPOINTS");
        }
        List<String> changed = new ArrayList<>();
        if (req.name() != null && !req.name().equals(s.getName())) changed.add("name");
        if (req.contactName() != null && !req.contactName().equals(s.getContactName())) changed.add("contactName");
        if (req.contactPhone() != null && !req.contactPhone().equals(s.getContactPhone())) changed.add("contactPhone");
        if (req.paymentTermsDays() != null && req.paymentTermsDays() != s.getPaymentTermsDays()) changed.add("paymentTermsDays");
        boolean emailChanged = req.contactEmail() != null && !req.contactEmail().equalsIgnoreCase(s.getContactEmail());
        String oldEmail = s.getContactEmail();

        s.update(req.name(), req.contactName(), req.contactEmail(), req.contactPhone(),
                req.bbbeeLevel(), req.bbbeeExpiry(), req.paymentTermsDays(), req.notes());
        ScSupplier saved = supplierRepo.save(s);

        String actor = TenantContext.getCurrentUserName();
        UUID actorId = currentUserIdOrNull();
        if (!changed.isEmpty())
            supplierAudit.record(tenantId.getValue(), id, "UPDATED", null, null, "Changed: " + String.join(", ", changed), actorId, actor);
        // A changed contact e-mail is audited on its own: it is the address bank-change alerts go to,
        // so attackers change it first. SupplierBankChangeService warns when a bank change follows within 14 days.
        if (emailChanged)
            supplierAudit.record(tenantId.getValue(), id, "CONTACT_EMAIL_CHANGED", null, null,
                    (oldEmail != null ? oldEmail : "none") + " -> " + req.contactEmail(), actorId, actor);
        return saved;
    }

    // ── Supplier items (pricing catalogue) ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScSupplierItem> getSupplierItems(TenantId tenantId, UUID supplierId) {
        getSupplier(tenantId, supplierId);
        return supplierItemRepo.findBySupplierId(supplierId);
    }

    @Transactional(readOnly = true)
    public List<ScSupplierItem> getBestPriceForItem(TenantId tenantId, UUID catalogueItemId) {
        return supplierItemRepo.findByCatalogueItemOrderedByPrice(tenantId.getValue(), catalogueItemId);
    }

    @Transactional
    public ScSupplierItem addSupplierItem(TenantId tenantId, UUID supplierId, AddSupplierItemRequest req) {
        getSupplier(tenantId, supplierId);
        ScSupplierItem item = ScSupplierItem.create(
                tenantId.getValue(), supplierId,
                req.catalogueItemId(), req.itemName(), req.supplierSku(),
                req.unitCost(), req.leadTimeDays() != null ? req.leadTimeDays() : 7,
                req.minOrderQty(), Boolean.TRUE.equals(req.isPreferred()));
        return supplierItemRepo.save(item);
    }

    // ── Stock locations ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScStockLocation> getLocations(TenantId tenantId) {
        return locationRepo.findActiveByTenantId(tenantId.getValue());
    }

    @Transactional
    public ScStockLocation createLocation(TenantId tenantId, CreateLocationRequest req) {
        boolean isFirst = locationRepo.findActiveByTenantId(tenantId.getValue()).isEmpty();
        ScStockLocation loc = ScStockLocation.create(tenantId.getValue(), req.name(),
                req.locationType(), req.address(),
                isFirst || Boolean.TRUE.equals(req.isDefault()));
        return locationRepo.save(loc);
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScInventory> getInventory(TenantId tenantId, UUID locationId) {
        UUID tid = tenantId.getValue();
        return locationId != null
                ? inventoryRepo.findByTenantIdAndLocation(tid, locationId)
                : inventoryRepo.findByTenantId(tid);
    }

    @Transactional(readOnly = true)
    public List<ScInventory> getLowStockAlerts(TenantId tenantId) {
        return inventoryRepo.findLowStock(tenantId.getValue());
    }

    /**
     * Sets opening stock for an item at a location.
     * FIX C-3 / C-4: atomic upsertInventoryRow(). FIX M-7: zero-delta guard.
     */
    @Transactional
    public ScInventory openingStock(TenantId tenantId, UUID userId, String userName,
                                    OpeningStockRequest req) {
        UUID tid = tenantId.getValue();
        locationRepo.findActiveByTenantIdAndId(tid, req.locationId())
                .orElseThrow(() -> new HandyFlowException("Location not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        inventoryRepo.upsertInventoryRow(UUID.randomUUID(), tid, req.locationId(), req.catalogueItemId());
        ScInventory inv = inventoryRepo.lockByTenantIdAndLocationIdAndCatalogueItemId(
                tid, req.locationId(), req.catalogueItemId()).orElseThrow();

        BigDecimal before = inv.getQtyOnHand();
        inv.setReorderLevels(req.reorderPoint(), req.reorderQty());
        if (req.binLocation() != null) inv.setBinLocation(req.binLocation());

        BigDecimal delta = req.qty().subtract(before);

        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            log.info("[SCM] Opening stock: no qty change for inventory={} — reorder levels updated only", inv.getId());
            return inventoryRepo.save(inv);
        }

        BigDecimal cost = req.unitCost() != null ? req.unitCost() : BigDecimal.ZERO;
        ScStockMovement mv = ScStockMovement.record(tid, inv, "OPENING",
                delta, cost, "OPENING", null, null, userId, userName);
        inv.adjustQty(delta, cost);
        inventoryRepo.save(inv);
        movementRepo.save(mv);
        return inv;
    }

    /** FIX H-5: verify the inventory record belongs to this tenant before returning data. */
    @Transactional(readOnly = true)
    public List<ScStockMovement> getMovements(TenantId tenantId, UUID inventoryId, Pageable pageable) {
        inventoryRepo.findByTenantIdAndId(tenantId.getValue(), inventoryId)
                .orElseThrow(() -> new HandyFlowException("Inventory record not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        return movementRepo.findByInventoryId(inventoryId, pageable).getContent();
    }

    // ── Purchase Orders ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScPurchaseOrder> getPurchaseOrders(TenantId tenantId, String status, Pageable pageable) {
        UUID tid = tenantId.getValue();
        if (status != null && !status.isBlank()) {
            PoStatus statusEnum = PoStatus.valueOf(status.toUpperCase());
            return poRepo.findByTenantIdAndStatus(tid, statusEnum, pageable);
        }
        return poRepo.findByTenantId(tid, pageable);
    }

    @Transactional(readOnly = true)
    public ScPurchaseOrder getPurchaseOrder(TenantId tenantId, UUID id) {
        return poRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Purchase order not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    /**
     * Creates a purchase order.
     * FIX C-1: atomic PO number. FIX H-1: createdByName from TenantContext.
     * Guard: rejects orders against suppliers that aren't orderable (blacklisted/inactive).
     */
    @Transactional
    public ScPurchaseOrder createPurchaseOrder(TenantId tenantId, UUID userId, String userName,
                                               CreatePurchaseOrderRequest req) {
        ScSupplier supplier = getSupplier(tenantId, req.supplierId());
        if (!supplier.isOrderable()) {
            throw new HandyFlowException(
                    "Cannot create a purchase order for a " + supplier.getStatus() + " supplier",
                    HttpStatus.BAD_REQUEST, "SUPPLIER_NOT_ORDERABLE");
        }

        long seq = sequenceService.nextValue(tenantId, "PO");
        String orderNumber = "PO-" + String.format("%05d", seq);

        ScPurchaseOrder po = ScPurchaseOrder.create(tenantId.getValue(), orderNumber,
                supplier.getId(), supplier.getName(),
                req.deliverToLocation(),
                req.orderDate() != null ? req.orderDate() : LocalDate.now(),
                req.requiredByDate(), req.currency(), req.projectRef(),
                req.notes(), req.internalNotes(), userId, userName);
        return poRepo.save(po);
    }

    @Transactional
    public ScPurchaseOrder submitForApproval(TenantId tenantId, UUID id) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, id);
        if (poLineRepo.countByPurchaseOrderId(id) == 0)
            throw new HandyFlowException("Cannot submit a PO with no lines",
                    HttpStatus.BAD_REQUEST, "NO_LINES");
        po.submit();
        ScPurchaseOrder saved = poRepo.save(po);
        approvalFacade.submit(tenantId, PO_APPROVALS_MODULE, PO_APPROVALS_ENTITY_TYPE, id, null, Map.of());
        return saved;
    }

    /**
     * Migrated onto the shared approval engine (backlog 1.1) — see ChangeOrderService.
     * NEW (chunk 1): segregation-of-duties check before anything else.
     */
    @Transactional
    public ScPurchaseOrder approvePurchaseOrder(TenantId tenantId, UUID id,
                                                UUID approverId, String approverName) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, id);
        enforceSegregation(tenantId, "SCM_ORDER", "ScPurchaseOrder", id, po.getCreatedBy(), approverId, "purchase order");
        priceOverrideGate.requireNonePending(tenantId, id);

        var existing = approvalFacade.getLatestRequestForEntity(tenantId, PO_APPROVALS_MODULE, PO_APPROVALS_ENTITY_TYPE, id);
        ApprovalRequestResponse result;
        if (existing.isPresent() && isPoApprovalOpen(existing.get())) {
            ApprovalStepResponse pendingStep = firstPoPendingStep(existing.get())
                    .orElseThrow(() -> new HandyFlowException(
                            "This PO's approval request has no pending step — data inconsistency, needs manual review",
                            HttpStatus.CONFLICT, "NO_PENDING_STEP"));
            result = approvalFacade.actOnStep(tenantId, pendingStep.id(), approverId,
                    currentUserAuthorities(), "APPROVE", null, null);
        } else {
            result = approvalFacade.submit(tenantId, PO_APPROVALS_MODULE, PO_APPROVALS_ENTITY_TYPE, id, approverId, Map.of());
            if (isPoApprovalOpen(result)) {
                ApprovalStepResponse firstStep = firstPoPendingStep(result)
                        .orElseThrow(() -> new IllegalStateException("A freshly-submitted request has no pending step"));
                result = approvalFacade.actOnStep(tenantId, firstStep.id(), approverId,
                        currentUserAuthorities(), "APPROVE", null, null);
            }
        }

        if (!"APPROVED".equals(result.status())) {
            throw new HandyFlowException(
                    "Approval did not complete — status: " + result.status(),
                    HttpStatus.BAD_REQUEST, "APPROVAL_NOT_COMPLETE");
        }

        po.approve(approverId, approverName);
        // NEW (chunk 7): budget check (409 BUDGET_EXCEEDED on an un-overridden hard stop) and commitment.
        // Throwing here rolls the whole approval back.
        budgetService.commitForPo(tenantId, po, approverId, approverName);
        log.info("[SCM] PO {} approved by {}", po.getOrderNumber(), approverName);
        ScPurchaseOrder saved = poRepo.save(po);
        // NEW (chunk 3): immutable revision snapshot of exactly what was approved.
        poVersionService.recordApproval(saved, approverId, approverName);
        warnIfAboveContractPrice(tenantId, saved);
        notificationService.notifyPoApproved(tenantId.getValue(), po.getOrderNumber(),
                po.getSupplierName(), po.getTotalAmount(), approverName);
        return saved;
    }

    @Transactional
    public ScPurchaseOrder rejectPurchaseOrder(TenantId tenantId, UUID id, String reason) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, id);

        var existing = approvalFacade.getLatestRequestForEntity(tenantId, PO_APPROVALS_MODULE, PO_APPROVALS_ENTITY_TYPE, id);
        if (existing.isPresent() && isPoApprovalOpen(existing.get())) {
            firstPoPendingStep(existing.get()).ifPresent(step ->
                    approvalFacade.actOnStep(tenantId, step.id(), currentPoUserId(),
                            currentUserAuthorities(), "REJECT", reason, null));
        }

        po.reject(reason);
        ScPurchaseOrder saved = poRepo.save(po);
        notificationService.notifyPoRejected(tenantId.getValue(), po.getOrderNumber(), reason);
        return saved;
    }

    @Transactional
    public ScPurchaseOrder markSent(TenantId tenantId, UUID id) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, id);

        // NEW (chunk 1): a supplier blocked/deactivated AFTER the PO was approved must not
        // receive it. The PO stays APPROVED so it can be re-sourced or the block lifted.
        ScSupplier supplier = getSupplier(tenantId, po.getSupplierId());
        if (!supplier.isOrderable()) {
            throw new HandyFlowException(
                    "Cannot send this PO — supplier " + supplier.getName() + " is now " + supplier.getStatus(),
                    HttpStatus.CONFLICT, "SUPPLIER_NOT_ORDERABLE");
        }

        po.send();
        ScPurchaseOrder saved = poRepo.save(po);

        // PDF failure must never block the send transition or the supplier email.
        byte[] pdfBytes = null;
        try {
            pdfBytes = buildPoPdfBytes(tenantId, po, supplier);
        } catch (Exception e) {
            log.error("[SCM] Failed to generate PO PDF for supplier email, po={}: {}", po.getOrderNumber(), e.getMessage());
        }

        notificationService.notifyPoSentToSupplier(tenantId.getValue(),
                supplier.getContactEmail(), supplier.getName(),
                po.getOrderNumber(), po.getTotalAmount(),
                po.getRequiredByDate(),
                pdfBytes);

        return saved;
    }

    @Transactional
    public ScPurchaseOrder addPurchaseOrderLine(TenantId tenantId, UUID poId, AddPoLineRequest req) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, poId);
        if (po.getStatus() != PoStatus.DRAFT)
            throw new HandyFlowException("Lines can only be added to DRAFT purchase orders",
                    HttpStatus.BAD_REQUEST, "INVALID_STATUS");

        ScPoLine line = ScPoLine.create(tenantId.getValue(), po.getId(),
                req.catalogueItemId(), req.itemName(), req.supplierSku(),
                req.qtyOrdered(), req.unitCost(),
                req.vatRate() != null ? req.vatRate() : vatRateProvider.ratePercent());
        poLineRepo.save(line);
        recalculatePoTotals(po);
        return poRepo.save(po);
    }

    @Transactional(readOnly = true)
    public List<ScPoLine> getPurchaseOrderLines(TenantId tenantId, UUID poId) {
        getPurchaseOrder(tenantId, poId);
        return poLineRepo.findByPurchaseOrderId(poId);
    }

    /**
     * Formal Purchase Order PDF. Tenant address deliberately not attempted — Tenant.address
     * is a JSONB map with unverified key names; tenantVat IS a flat column.
     */
    @Transactional(readOnly = true)
    public byte[] generatePoPdf(TenantId tenantId, UUID poId) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, poId);
        ScSupplier supplier = getSupplier(tenantId, po.getSupplierId());
        return buildPoPdfBytes(tenantId, po, supplier);
    }

    private byte[] buildPoPdfBytes(TenantId tenantId, ScPurchaseOrder po, ScSupplier supplier) {
        List<ScPoLine> lines = poLineRepo.findByPurchaseOrderId(po.getId());
        String[] tenantDetails = findTenantDetails(tenantId.getValue());
        return poPdfGenerator.generate(po, lines, tenantDetails[0], tenantDetails[1],
                formatSupplierAddress(supplier), supplier.getContactName(), supplier.getContactPhone());
    }

    /** Goods Received Note PDF — see generatePoPdf() for the tenant-address scoping decision. */
    @Transactional(readOnly = true)
    public byte[] generateGrnPdf(TenantId tenantId, UUID grId) {
        UUID tid = tenantId.getValue();
        ScGoodsReceipt gr = grRepo.findByTenantIdAndId(tid, grId)
                .orElseThrow(() -> new HandyFlowException("Goods receipt not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        List<ScGrLine> lines = grLineRepo.findByGoodsReceiptId(gr.getId());
        ScSupplier supplier = getSupplier(tenantId, gr.getSupplierId());
        String[] tenantDetails = findTenantDetails(tid);

        String poNumber = null;
        if (gr.getPurchaseOrderId() != null) {
            poNumber = poRepo.findByTenantIdAndId(tid, gr.getPurchaseOrderId())
                    .map(ScPurchaseOrder::getOrderNumber)
                    .orElse(null);
        }

        String locationName = null;
        String locationAddress = null;
        if (gr.getReceivedTo() != null) {
            var loc = locationRepo.findActiveByTenantIdAndId(tid, gr.getReceivedTo()).orElse(null);
            if (loc != null) {
                locationName = loc.getName();
                locationAddress = loc.getAddress();
            }
        }

        return grnPdfGenerator.generate(gr, lines, tenantDetails[0], tenantDetails[1],
                poNumber, locationName, locationAddress,
                supplier.getName(), formatSupplierAddress(supplier),
                supplier.getContactName(), supplier.getContactPhone());
    }

    // ── Goods Receipts ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScGoodsReceipt> getGoodsReceipts(TenantId tenantId, Pageable pageable) {
        return grRepo.findByTenantId(tenantId.getValue(), pageable);
    }

    @Transactional(readOnly = true)
    public List<ScGoodsReceipt> getGoodsReceiptsForPurchaseOrder(TenantId tenantId, UUID poId) {
        getPurchaseOrder(tenantId, poId);
        return grRepo.findByTenantIdAndPurchaseOrderId(tenantId.getValue(), poId);
    }

    // CHANGED (chunk 2): receiving logic moved to GoodsReceivingService (inspection, partial /
    // rejected quantities, over-receipt protection, locking, reversal). Signatures unchanged so
    // ScmController keeps working as-is.

    @Transactional
    public ScGoodsReceipt createGoodsReceipt(TenantId tenantId, UUID userId, String userName,
                                             CreateGoodsReceiptRequest req) {
        return receivingService.create(tenantId, userId, userName, req);
    }

    @Transactional
    public ScGoodsReceipt postGoodsReceipt(TenantId tenantId, UUID grId, UUID userId, String userName,
                                           List<PostGrLineRequest> lines) {
        return receivingService.post(tenantId, grId, userId, userName, lines);
    }

    // ── Supplier Invoices ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScSupplierInvoice> getSupplierInvoices(TenantId tenantId, String status, Pageable pageable) {
        UUID tid = tenantId.getValue();
        if (status != null && !status.isBlank()) {
            InvoiceStatus statusEnum = InvoiceStatus.valueOf(status.toUpperCase());
            return invoiceRepo.findByTenantIdAndStatus(tid, statusEnum, pageable);
        }
        return invoiceRepo.findByTenantId(tid, pageable);
    }

    /**
     * Captures a supplier invoice and runs line-level three-way matching.
     * <p>
     * CHANGED (chunk 1):
     *  - validates supplier (tenant-scoped — previously any supplierId was accepted),
     *    PO existence, required fields and subtotal + VAT = total;
     *  - blocks duplicates (same supplier + same supplier invoice ref) with a 409, both
     *    via an explicit check (friendly message) and a DB unique index (race-safe);
     *  - stores invoice lines (totals computed server-side) when supplied;
     *  - records who captured it, for the capturer-cannot-approve control;
     *  - matching is delegated to InvoiceMatchingService (engine + persisted exceptions).
     */
    @Transactional
    public ScSupplierInvoice createSupplierInvoice(TenantId tenantId, CreateSupplierInvoiceRequest req) {
        UUID tid = tenantId.getValue();

        if (req.supplierId() == null)
            throw invalidInvoice("supplierId is required");
        if (req.invoiceDate() == null || req.dueDate() == null)
            throw invalidInvoice("invoiceDate and dueDate are required");
        if (req.subtotal() == null || req.totalAmount() == null)
            throw invalidInvoice("subtotal and totalAmount are required");
        BigDecimal vat = req.vatAmount() != null ? req.vatAmount() : BigDecimal.ZERO;
        if (req.subtotal().add(vat).subtract(req.totalAmount()).abs().compareTo(new BigDecimal("0.02")) > 0)
            throw invalidInvoice("subtotal + VAT must equal the total amount");

        getSupplier(tenantId, req.supplierId());                       // 404 if not this tenant's supplier
        if (req.purchaseOrderId() != null)
            getPurchaseOrder(tenantId, req.purchaseOrderId());         // 404 if unknown PO

        String ref = req.supplierInvoiceRef() != null ? req.supplierInvoiceRef().trim() : null;
        if (ref != null && !ref.isEmpty()
                && invoiceRepo.countActiveWithRef(tid, req.supplierId(), ref, InvoiceStatus.CANCELLED) > 0) {
            throw new HandyFlowException(
                    "Supplier invoice '" + ref + "' has already been captured for this supplier",
                    HttpStatus.CONFLICT, "DUPLICATE_INVOICE");
        }

        long seq = sequenceService.nextValue(tenantId, "SINV");
        String invoiceNumber = "SINV-" + String.format("%05d", seq);

        ScSupplierInvoice inv = ScSupplierInvoice.create(tid, invoiceNumber,
                req.supplierId(), req.purchaseOrderId(), req.goodsReceiptId(),
                ref, req.invoiceDate(), req.dueDate(),
                req.currency(), req.subtotal(), vat, req.totalAmount(),
                req.notes(), currentUserIdOrNull());

        if (req.purchaseOrderId() == null) inv.codeTo(req.department(), req.costCentre(), req.projectRef());
        List<ScSupplierInvoiceLine> lines = buildInvoiceLines(tid, inv.getId(), req.lines());

        ScSupplierInvoice saved;
        try {
            saved = invoiceRepo.saveAndFlush(inv);
        } catch (DataIntegrityViolationException e) {
            // Lost a race against the unique index on (tenant, supplier, ref).
            throw new HandyFlowException(
                    "Supplier invoice '" + ref + "' has already been captured for this supplier",
                    HttpStatus.CONFLICT, "DUPLICATE_INVOICE");
        }
        if (!lines.isEmpty()) invoiceLineRepo.saveAll(lines);

        saved = matchingService.runMatch(tenantId, saved);
        log.info("[SCM] Supplier invoice {} created matchStatus={} lines={}",
                invoiceNumber, saved.getMatchStatus(), lines.size());
        return saved;
    }

    /**
     * CHANGED (chunk 1): approval now requires a cleared match and respects segregation
     * of duties. The AP hand-off is idempotent and shared with the override path.
     */
    @Transactional
    public ScSupplierInvoice approveSupplierInvoice(TenantId tenantId, UUID id,
                                                    UUID approverId, String approverName) {
        ScSupplierInvoice inv = invoiceRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Invoice not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));

        requireNoOpenCriticalException(tenantId, inv);
        requireMatchClearedForApproval(inv);
        requireCodingForBudgets(tenantId, inv);
        enforceSegregation(tenantId, "SCM_INVOICE", "ScSupplierInvoice", inv.getId(), inv.getCapturedBy(), approverId, "supplier invoice");

        inv.approve(approverId, approverName);
        log.info("[SCM] Supplier invoice {} approved by {}", inv.getInvoiceNumber(), approverName);

        handOffToAp(tenantId, inv, approverId);
        return invoiceRepo.save(inv);
    }

    /**
     * An invoice with no purchase order has no commitment to draw on, so it reaches a budget only through its coding. While
     * the tenant has budgets that control spending, approving an uncoded one would let the spend bypass them: refuse it.
     */
    private void requireCodingForBudgets(TenantId tenantId, ScSupplierInvoice inv) {
        if (inv.getPurchaseOrderId() != null) return;
        boolean coded = inv.getDepartment() != null || inv.getCostCentre() != null || inv.getProjectRef() != null;
        if (!coded && budgetService.hasActiveBudgets(tenantId))
            throw new HandyFlowException("Invoice " + inv.getInvoiceNumber() + " has no purchase order and is not coded to a department, cost centre or project, "
                    + "so it would count against no budget. Code it first.", HttpStatus.CONFLICT, "CODING_REQUIRED");
    }

    /** Sets or changes the coding of a no-PO invoice that is not yet approved. */
    @Transactional
    public ScSupplierInvoice codeSupplierInvoice(TenantId tenantId, UUID id, String department, String costCentre, String projectRef) {
        ScSupplierInvoice inv = invoiceRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Invoice not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (inv.getPurchaseOrderId() != null)
            throw new HandyFlowException("This invoice is on a purchase order, which carries the coding", HttpStatus.CONFLICT, "HAS_PO");
        if (inv.getStatus() == InvoiceStatus.APPROVED || inv.getStatus() == InvoiceStatus.PAID)
            throw new HandyFlowException("An approved or paid invoice can no longer be recoded", HttpStatus.CONFLICT, "INVOICE_LOCKED");
        inv.codeTo(department, costCentre, projectRef);
        return invoiceRepo.save(inv);
    }

    @Transactional
    public ScSupplierInvoice markPaid(TenantId tenantId, UUID id, String paymentRef) {
        ScSupplierInvoice inv = invoiceRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Invoice not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        requireNoOpenCriticalException(tenantId, inv);
        try {
            inv.markPaid(paymentRef);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "ALREADY_HANDED_OFF");
        }
        return invoiceRepo.save(inv);
    }

    /** Remittance advice — PAID invoices only; see ScRemittanceAdvicePdfGenerator. */
    @Transactional(readOnly = true)
    public byte[] generateRemittanceAdvicePdf(TenantId tenantId, UUID invoiceId) {
        ScSupplierInvoice inv = invoiceRepo.findByTenantIdAndId(tenantId.getValue(), invoiceId)
                .orElseThrow(() -> new HandyFlowException("Invoice not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (inv.getStatus() != InvoiceStatus.PAID) {
            throw new IllegalArgumentException(
                    "Remittance advice is only available for paid invoices — current status: " + inv.getStatus());
        }
        ScSupplier supplier = getSupplier(tenantId, inv.getSupplierId());
        String[] tenantDetails = findTenantDetails(tenantId.getValue());

        return remittanceAdvicePdfGenerator.generate(inv, tenantDetails[0], tenantDetails[1],
                supplier.getName(), formatSupplierAddress(supplier),
                supplier.getContactName(), supplier.getContactPhone());
    }

    /**
     * Resolves a DISPUTED invoice by override-approve.
     * CHANGED (chunk 1):
     *  - FIX: this path previously approved the invoice WITHOUT handing it to AP, leaving
     *    an approved invoice with no AP bill — and markPaid() would then succeed inside
     *    Supply Chain (apBillId was null), bypassing AP entirely. It now hands off exactly
     *    like normal approval.
     *  - a written reason is mandatory; segregation of duties applies.
     */
    @Transactional
    public ScSupplierInvoice overrideDisputedInvoice(TenantId tenantId, UUID id,
                                                     UUID approverId, String approverName, String reason) {
        if (reason == null || reason.isBlank())
            throw new HandyFlowException("An override reason is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");

        ScSupplierInvoice inv = invoiceRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Invoice not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        enforceSegregation(tenantId, "SCM_INVOICE", "ScSupplierInvoice", inv.getId(), inv.getCapturedBy(), approverId, "supplier invoice");

        inv.overrideDisputeAndApprove(approverId, approverName, reason.trim());
        matchingService.resolveOpenExceptions(tenantId, id);
        controlExceptionFacade.resolveForEntity(tenantId, "ScSupplierInvoice", id,
                approverId, approverName, "Overridden: " + reason);
        handOffToAp(tenantId, inv, approverId);
        log.info("[SCM] Invoice {} dispute overridden and approved by {}", inv.getInvoiceNumber(), approverName);
        return invoiceRepo.save(inv);
    }

    /** Rejects a supplier invoice (any status except PAID). */
    @Transactional
    public ScSupplierInvoice cancelSupplierInvoice(TenantId tenantId, UUID id, String reason) {
        ScSupplierInvoice inv = invoiceRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Invoice not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (inv.getApBillId() != null) {
            // The AP bill is already payable; cancelling here would leave it payable with no approved invoice behind it.
            throw new HandyFlowException("Invoice " + inv.getInvoiceNumber() + " has been handed off to AP (bill "
                    + inv.getApBillId() + ") — void the bill in Accounts Payable instead",
                    HttpStatus.CONFLICT, "ALREADY_HANDED_OFF");
        }
        inv.cancel(reason);
        controlExceptionFacade.resolveForEntity(tenantId, "ScSupplierInvoice", id,
                null, "System", "Invoice cancelled: " + reason);
        log.info("[SCM] Invoice {} cancelled: {}", inv.getInvoiceNumber(), reason);
        return invoiceRepo.save(inv);
    }

    // ── Supplier invoice attachments ────────────────────────────────────────
    // Base64-in-DB (no object storage yet) — see ScSupplierInvoiceAttachment's Javadoc.

    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;

    @Transactional
    public AttachmentResponse uploadInvoiceAttachment(TenantId tenantId, UUID invoiceId,
                                                      UploadAttachmentRequest req,
                                                      UUID uploadedBy, String uploadedByName) {
        invoiceRepo.findByTenantIdAndId(tenantId.getValue(), invoiceId)
                .orElseThrow(() -> new HandyFlowException("Invoice not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));

        // Base64 is ~4/3 the size of the raw bytes — an estimate, deliberately not decoded.
        long approxDecodedBytes = (req.fileContentBase64().length() * 3L) / 4;
        if (approxDecodedBytes > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException(
                    "File is too large — maximum attachment size is "
                            + (MAX_ATTACHMENT_BYTES / (1024 * 1024)) + "MB");
        }

        ScSupplierInvoiceAttachment attachment = ScSupplierInvoiceAttachment.create(
                tenantId.getValue(), invoiceId, req.fileName(),
                req.contentType() != null ? req.contentType() : "application/octet-stream",
                req.fileSizeBytes(), req.fileContentBase64(), uploadedBy, uploadedByName);
        attachmentRepo.save(attachment);
        log.info("[SCM] Attachment '{}' uploaded for invoice={}", req.fileName(), invoiceId);
        return toAttachmentResponse(attachment);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> getInvoiceAttachments(TenantId tenantId, UUID invoiceId) {
        invoiceRepo.findByTenantIdAndId(tenantId.getValue(), invoiceId)
                .orElseThrow(() -> new HandyFlowException("Invoice not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        return attachmentRepo.findSummariesByInvoice(tenantId.getValue(), invoiceId).stream()
                .map(p -> new AttachmentResponse(p.getId(), p.getFileName(), p.getContentType(),
                        p.getFileSizeBytes(), p.getUploadedByName(), p.getCreatedAt()))
                .toList();
    }

    public record AttachmentFile(byte[] content, String contentType, String fileName) {}

    @Transactional(readOnly = true)
    public AttachmentFile downloadInvoiceAttachment(TenantId tenantId, UUID invoiceId, UUID attachmentId) {
        invoiceRepo.findByTenantIdAndId(tenantId.getValue(), invoiceId)
                .orElseThrow(() -> new HandyFlowException("Invoice not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        ScSupplierInvoiceAttachment a = attachmentRepo.findByTenantIdAndId(tenantId.getValue(), attachmentId)
                .orElseThrow(() -> new HandyFlowException("Attachment not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!a.getSupplierInvoiceId().equals(invoiceId)) {
            throw new HandyFlowException("Attachment does not belong to this invoice",
                    HttpStatus.BAD_REQUEST, "MISMATCHED_INVOICE");
        }
        byte[] content = java.util.Base64.getDecoder().decode(a.getFileContentBase64());
        String contentType = a.getContentType() != null && !a.getContentType().isBlank()
                ? a.getContentType() : "application/octet-stream";
        return new AttachmentFile(content, contentType, a.getFileName());
    }

    @Transactional
    public void deleteInvoiceAttachment(TenantId tenantId, UUID invoiceId, UUID attachmentId) {
        invoiceRepo.findByTenantIdAndId(tenantId.getValue(), invoiceId)
                .orElseThrow(() -> new HandyFlowException("Invoice not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        ScSupplierInvoiceAttachment a = attachmentRepo.findByTenantIdAndId(tenantId.getValue(), attachmentId)
                .orElseThrow(() -> new HandyFlowException("Attachment not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!a.getSupplierInvoiceId().equals(invoiceId)) {
            throw new HandyFlowException("Attachment does not belong to this invoice",
                    HttpStatus.BAD_REQUEST, "MISMATCHED_INVOICE");
        }
        attachmentRepo.delete(a);
    }

    private AttachmentResponse toAttachmentResponse(ScSupplierInvoiceAttachment a) {
        return new AttachmentResponse(a.getId(), a.getFileName(), a.getContentType(),
                a.getFileSizeBytes(), a.getUploadedByName(), a.getCreatedAt());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private HandyFlowException invalidInvoice(String msg) {
        return new HandyFlowException(msg, HttpStatus.BAD_REQUEST, "INVALID_INVOICE");
    }

    /** Builds invoice lines; totals are computed in ScSupplierInvoiceLine.create(), never taken from the client. */
    private List<ScSupplierInvoiceLine> buildInvoiceLines(UUID tid, UUID invoiceId,
                                                          List<CreateSupplierInvoiceRequest.InvoiceLineRequest> reqLines) {
        if (reqLines == null || reqLines.isEmpty()) return List.of();
        List<ScSupplierInvoiceLine> out = new ArrayList<>();
        int n = 1;
        for (var l : reqLines) {
            if (l.quantity() == null || l.quantity().compareTo(BigDecimal.ZERO) <= 0)
                throw invalidInvoice("Line " + n + ": quantity must be greater than zero");
            if (l.unitPrice() == null || l.unitPrice().compareTo(BigDecimal.ZERO) < 0)
                throw invalidInvoice("Line " + n + ": unitPrice is required");

            ScPoLine poLine = l.poLineId() != null
                    ? poLineRepo.findByIdAndTenantId(l.poLineId(), tid).orElse(null) : null;
            String name = l.itemName() != null && !l.itemName().isBlank() ? l.itemName()
                    : poLine != null ? poLine.getItemName() : null;
            if (name == null) throw invalidInvoice("Line " + n + ": itemName is required");

            // Default VAT to the PO line's own rate when linked — falling back to the global
            // rate would raise false TAX_VARIANCE findings on zero-rated/exempt PO lines.
            BigDecimal vatRate = l.vatRate() != null ? l.vatRate()
                    : poLine != null ? poLine.getVatRate() : vatRateProvider.ratePercent();

            out.add(ScSupplierInvoiceLine.create(tid, invoiceId, n++, l.poLineId(), name,
                    l.quantity(), l.unitPrice(), vatRate));
        }
        return out;
    }

    /** Only a cleared match (or a genuine non-PO invoice) may be approved for payment. */
    /**
     * Independent of matchStatus on purpose: matchStatus is a summary field, the open exception rows are the facts.
     * Approval and payment both stop while a critical (DISPUTE-class) exception is open; override resolves them.
     */
    private void requireNoOpenCriticalException(TenantId tenantId, ScSupplierInvoice inv) {
        long open = matchingService.openBlockingExceptionCount(tenantId, inv.getId());
        if (open > 0) {
            throw new HandyFlowException("Invoice " + inv.getInvoiceNumber() + " has " + open
                    + " open critical match exception(s) — resolve them or use the override with a reason",
                    HttpStatus.CONFLICT, "CRITICAL_EXCEPTION_OPEN");
        }
    }

    private void requireMatchClearedForApproval(ScSupplierInvoice inv) {
        MatchStatus ms = inv.getMatchStatus();
        if (ms == MatchStatus.MATCHED || ms == MatchStatus.OVERRIDDEN) return;
        if (ms == MatchStatus.PENDING && inv.getPurchaseOrderId() == null) return;
        String why = switch (ms) {
            case PARTIAL_MATCH -> "is on hold — goods/receipt/detail still outstanding (" + inv.getMatchNotes() + ")";
            case DISPUTE -> "is disputed (" + inv.getMatchNotes() + ") — use override or cancel";
            default -> "has not completed matching — re-run the match first";
        };
        throw new HandyFlowException("Invoice " + inv.getInvoiceNumber() + " cannot be approved: it " + why,
                HttpStatus.CONFLICT, "MATCH_NOT_CLEARED");
    }

    /** Idempotent: if a bill already exists for this invoice, never create a second one. */
    private void handOffToAp(TenantId tenantId, ScSupplierInvoice inv, UUID approverId) {
        if (inv.getApBillId() != null) {
            log.info("[SCM] Invoice {} already handed off to AP bill {} — skipping", inv.getInvoiceNumber(), inv.getApBillId());
            return;
        }
        String supplierName = supplierRepo.findByTenantIdAndId(tenantId.getValue(), inv.getSupplierId())
                .map(ScSupplier::getName).orElse("Unknown Supplier");
        ApBill bill = apFacade.createBillFromSupplyChainInvoice(tenantId, inv.getSupplierId(), supplierName,
                inv.getInvoiceNumber(), inv.getInvoiceDate(), inv.getDueDate(),
                inv.getSubtotal(), inv.getVatAmount(), approverId);
        inv.recordApBillHandOff(bill.getId());
    }

    /** A PO priced above an active contract is flagged for review; the order itself is not blocked. */
    private void warnIfAboveContractPrice(TenantId tenantId, ScPurchaseOrder po) {
        try {
            var variances = contractService.priceVariances(tenantId, po.getId());
            if (variances.isEmpty()) return;
            StringBuilder msg = new StringBuilder("PO " + po.getOrderNumber() + " is priced above the contract: ");
            variances.forEach(v -> msg.append(v.itemName()).append(' ').append(v.poUnitCost()).append(" vs ").append(v.agreedUnitPrice())
                    .append(" (+").append(v.overPct()).append("% on ").append(v.contractNumber()).append("); "));
            controlExceptionFacade.raise(tenantId, "supplychain", "CONTRACT_PRICE_EXCEEDED", "ScPurchaseOrder", po.getId(), "WARNING", msg.toString());
        } catch (Exception e) {
            log.warn("[SCM] Contract price check failed for PO {}: {}", po.getOrderNumber(), e.getMessage());   // never blocks an approval
        }
    }

    private void enforceSegregation(TenantId tenantId, String checkingPermission, String itemType, UUID itemId,
                                    UUID creator, UUID actor, String what) {
        sod.require(tenantId, checkingPermission, creator, actor, "You cannot approve a " + what + " that you created yourself",
                itemType, itemId, what + " approval");
    }

    private UUID currentUserIdOrNull() {
        try {
            String id = TenantContext.getUserId();
            return id != null ? UUID.fromString(id) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isPoApprovalOpen(ApprovalRequestResponse r) {
        return "SUBMITTED".equals(r.status()) || "IN_PROGRESS".equals(r.status());
    }

    private java.util.Optional<ApprovalStepResponse> firstPoPendingStep(ApprovalRequestResponse r) {
        return r.steps().stream()
                .filter(s -> "PENDING".equals(s.status()))
                .min(Comparator.comparingInt(ApprovalStepResponse::stepOrder));
    }

    private List<String> currentUserAuthorities() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private UUID currentPoUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void recalculatePoTotals(ScPurchaseOrder po) {
        List<ScPoLine> lines = poLineRepo.findByPurchaseOrderId(po.getId());
        BigDecimal sub = lines.stream().map(ScPoLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vat = lines.stream().map(ScPoLine::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        po.recalculateTotals(sub, vat, sub.add(vat));
    }

    /** Returns [tenantName, tenantVat]. */
    private String[] findTenantDetails(UUID tenantId) {
        try {
            var row = jdbc.queryForMap(
                    "SELECT name, vat_number FROM tenants WHERE tenant_id = ?", tenantId);
            return new String[]{
                    (String) row.getOrDefault("name", "HandyFlow Tenant"),
                    (String) row.get("vat_number")
            };
        } catch (Exception e) {
            log.warn("[SCM] Could not fetch tenant details for PO PDF, tenant={}: {}", tenantId, e.getMessage());
            return new String[]{"HandyFlow Tenant", null};
        }
    }

    private String formatSupplierAddress(ScSupplier s) {
        return Stream.of(s.getStreet(), s.getSuburb(), s.getCity(), s.getProvince(), s.getPostalCode())
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.joining(", "));
    }

    // performThreeWayMatch() and MATCH_TOLERANCE were removed — see InvoiceMatchingService,
    // ThreeWayMatchEngine and ScmMatchTolerances.
}
