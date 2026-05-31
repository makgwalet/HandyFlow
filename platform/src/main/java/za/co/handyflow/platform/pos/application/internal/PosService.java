package za.co.handyflow.platform.pos.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.pos.domain.model.*;
import za.co.handyflow.platform.pos.domain.repository.*;
import za.co.handyflow.platform.pos.dto.*;
import za.co.handyflow.platform.shared.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosService {

    private final PosTransactionRepository     txnRepo;
    private final PosTransactionItemRepository txnItemRepo;
    private final PosStockItemRepository       stockRepo;
    private final PosStockMovementRepository   movementRepo;
    private final PosPurchaseOrderRepository   poRepo;
    private final PosPurchaseOrderItemRepository poItemRepo;
    private final JdbcTemplate                 jdbc;

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PosSummaryResponse getSummary(TenantId tenantId) {
        Instant todayStart  = LocalDate.now().atStartOfDay(ZoneId.of("Africa/Johannesburg")).toInstant();
        Instant monthStart  = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.of("Africa/Johannesburg")).toInstant();
        Instant now         = Instant.now();

        return new PosSummaryResponse(
                txnRepo.sumSalesBetween(tenantId, todayStart, now),
                txnRepo.sumSalesBetween(tenantId, monthStart, now),
                txnRepo.countSalesBetween(tenantId, todayStart, now),
                txnRepo.countSalesBetween(tenantId, monthStart, now),
                stockRepo.findAll(tenantId, Pageable.unpaged()).getTotalElements(),
                stockRepo.countLowStock(tenantId),
                countPendingOrders(tenantId)
        );
    }

    // ── POS Terminal — process sale ───────────────────────────────────────────

    @Transactional
    public TransactionResponse processSale(TenantId tenantId, UUID servedBy,
                                            String servedByName, ProcessSaleRequest req) {
        // Generate transaction number: TXN-00001
        int seq = txnRepo.findMaxTransactionSequence(tenantId) + 1;
        String txnNumber = "TXN-%05d".formatted(seq);

        PosTransaction txn = PosTransaction.create(tenantId, txnNumber,
                req.customerId(), req.customerName(),
                req.paymentMethod(), req.amountTendered(),
                req.paymentRef(), servedBy, servedByName, req.notes());
        txnRepo.save(txn);

        // Process each line item
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        List<PosTransactionItem> savedItems = new ArrayList<>();

        for (ProcessSaleRequest.SaleLineItem line : req.items()) {
            // Resolve item details from catalogue if catalogueItemId provided
            BigDecimal unitPrice = line.unitPrice();
            BigDecimal vatRate   = BigDecimal.valueOf(15);
            String     itemName  = line.itemName();
            String     sku       = null;

            if (line.catalogueItemId() != null) {
                var catalogueRow = fetchCatalogueItem(line.catalogueItemId());
                if (catalogueRow != null) {
                    if (unitPrice == null) unitPrice = (BigDecimal) catalogueRow.get("default_price");
                    vatRate   = (BigDecimal) catalogueRow.get("vat_rate");
                    itemName  = (String) catalogueRow.get("name");
                    sku       = (String) catalogueRow.get("sku");
                }
            }
            if (unitPrice == null) unitPrice = BigDecimal.ZERO;

            PosTransactionItem item = PosTransactionItem.create(
                    txn.getId(), tenantId.getValue(),
                    line.catalogueItemId(), itemName, sku,
                    line.qty(), unitPrice, vatRate, line.discountPct());
            txnItemRepo.save(item);
            savedItems.add(item);

            subtotal     = subtotal.add(item.getUnitPrice().multiply(item.getQty()));
            vatTotal     = vatTotal.add(item.getVatAmount());
            discountTotal = discountTotal.add(item.getDiscountAmount());

            // Deduct stock if tracked
            if (line.catalogueItemId() != null) {
                deductStock(tenantId, line.catalogueItemId(), line.qty(),
                        txn.getId(), servedBy);
            }
        }

        BigDecimal total = subtotal.subtract(discountTotal).add(vatTotal);
        txn.setTotals(subtotal, vatTotal, discountTotal, total);

        // Post accounting journal entry
        UUID journalId = postSaleJournal(tenantId, txn, subtotal, vatTotal, total);
        txn.setJournalEntry(journalId);
        txnRepo.save(txn);

        log.info("Sale processed: txn={} total={} method={}", txnNumber, total, req.paymentMethod());
        return toTransactionResponse(txn, savedItems);
    }

    @Transactional
    public TransactionResponse voidTransaction(TenantId tenantId, UUID id,
                                                VoidTransactionRequest req, UUID voidedBy) {
        PosTransaction txn = txnRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id.toString()));
        if (txn.isVoided()) throw new HandyFlowException(
                "Transaction already voided", HttpStatus.BAD_REQUEST, "ALREADY_VOIDED");

        txn.voidTransaction(req.reason());
        txnRepo.save(txn);

        // Reverse stock movements
        txnItemRepo.findByTransactionId(id).forEach(item -> {
            if (item.getCatalogueItemId() != null) {
                addStock(tenantId, item.getCatalogueItemId(), item.getQty(),
                        id, "RETURN", "Void: " + req.reason(), voidedBy);
            }
        });

        log.info("Transaction voided: txn={} reason={}", txn.getTransactionNumber(), req.reason());
        return toTransactionResponse(txn, txnItemRepo.findByTransactionId(id));
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(TenantId tenantId, Pageable pageable) {
        return txnRepo.findAll(tenantId, pageable)
                .map(t -> toTransactionResponse(t, txnItemRepo.findByTransactionId(t.getId())));
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(TenantId tenantId, UUID id) {
        PosTransaction txn = txnRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id.toString()));
        return toTransactionResponse(txn, txnItemRepo.findByTransactionId(id));
    }

    // ── Stock items ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<StockItemResponse> getStockItems(TenantId tenantId, Pageable pageable) {
        return stockRepo.findAll(tenantId, pageable).map(s -> toStockResponse(s));
    }

    @Transactional(readOnly = true)
    public List<StockItemResponse> getLowStockItems(TenantId tenantId) {
        return stockRepo.findLowStock(tenantId).stream().map(this::toStockResponse).toList();
    }

    @Transactional
    public StockItemResponse createStockItem(TenantId tenantId, CreateStockItemRequest req) {
        if (stockRepo.findByTenantIdAndCatalogueItemId(tenantId, req.catalogueItemId()).isPresent()) {
            throw new HandyFlowException("Stock item already exists for this product",
                    HttpStatus.BAD_REQUEST, "DUPLICATE_STOCK_ITEM");
        }
        PosStockItem item = PosStockItem.create(tenantId, req.catalogueItemId(),
                req.qtyOnHand(), req.reorderLevel(), req.reorderQty(),
                req.costPrice(), req.location());
        stockRepo.save(item);

        // Record opening movement if qty > 0
        if (req.qtyOnHand() != null && req.qtyOnHand().compareTo(BigDecimal.ZERO) > 0) {
            movementRepo.save(PosStockMovement.create(
                    tenantId.getValue(), item.getId(), "OPENING",
                    req.qtyOnHand(), BigDecimal.ZERO, req.qtyOnHand(),
                    null, null, "Opening stock", null));
        }
        return toStockResponse(item);
    }

    @Transactional
    public StockItemResponse updateStockItem(TenantId tenantId, UUID id, UpdateStockItemRequest req) {
        PosStockItem item = stockRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock Item", id.toString()));
        item.update(req.reorderLevel(), req.reorderQty(), req.costPrice(), req.location());
        stockRepo.save(item);
        return toStockResponse(item);
    }

    @Transactional(readOnly = true)
    public List<PosStockMovement> getStockMovements(TenantId tenantId, UUID stockItemId) {
        stockRepo.findByIdAndTenantId(stockItemId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock Item", stockItemId.toString()));
        return movementRepo.findByStockItemIdOrderByCreatedAtDesc(stockItemId);
    }

    // ── Barcode/SKU lookup ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public StockItemResponse lookupByBarcode(TenantId tenantId, String barcode) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT ci.id FROM catalogue_items ci WHERE ci.tenant_id = ? AND ci.barcode = ? AND ci.deleted_at IS NULL LIMIT 1",
                    tenantId.getValue(), barcode);
            UUID catalogueItemId = (UUID) row.get("id");
            return stockRepo.findByTenantIdAndCatalogueItemId(tenantId, catalogueItemId)
                    .map(this::toStockResponse)
                    .orElseThrow(() -> new HandyFlowException(
                            "No stock item found for barcode: " + barcode,
                            HttpStatus.NOT_FOUND, "NOT_FOUND"));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new HandyFlowException("Barcode not found: " + barcode,
                    HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
    }

    // ── Purchase orders ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> getPurchaseOrders(TenantId tenantId, Pageable pageable) {
        return poRepo.findAll(tenantId, pageable)
                .map(po -> toPurchaseOrderResponse(po, poItemRepo.findByPurchaseOrderId(po.getId())));
    }

    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(TenantId tenantId, UUID createdBy,
                                                      CreatePurchaseOrderRequest req) {
        int seq = poRepo.findMaxOrderSequence(tenantId) + 1;
        String orderNumber = "PO-%04d".formatted(seq);

        PosPurchaseOrder po = PosPurchaseOrder.create(tenantId, orderNumber,
                req.supplierId(), req.supplierName(),
                req.expectedDate(), req.notes(), createdBy);
        poRepo.save(po);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        List<PosPurchaseOrderItem> items = new ArrayList<>();

        for (CreatePurchaseOrderRequest.PurchaseOrderLine line : req.items()) {
            PosPurchaseOrderItem item = PosPurchaseOrderItem.create(
                    po.getId(), tenantId.getValue(),
                    line.catalogueItemId(), line.itemName(),
                    line.qtyOrdered(), line.unitCost(), line.vatRate());
            poItemRepo.save(item);
            items.add(item);
            subtotal = subtotal.add(item.getUnitCost().multiply(item.getQtyOrdered()));
            vatTotal = vatTotal.add(item.getLineTotal().subtract(item.getUnitCost().multiply(item.getQtyOrdered())));
        }

        po.setTotals(subtotal, vatTotal, subtotal.add(vatTotal));
        po.markOrdered();
        poRepo.save(po);

        log.info("Purchase order created: po={} supplier={}", orderNumber, req.supplierName());
        return toPurchaseOrderResponse(po, items);
    }

    @Transactional
    public PurchaseOrderResponse receiveStock(TenantId tenantId, UUID poId,
                                               ReceiveStockRequest req, UUID receivedBy) {
        PosPurchaseOrder po = poRepo.findByIdAndTenantId(poId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order", poId.toString()));
        if (!po.isOrdered()) throw new HandyFlowException(
                "Purchase order is not in an ordered state",
                HttpStatus.BAD_REQUEST, "INVALID_STATUS");

        List<PosPurchaseOrderItem> items = poItemRepo.findByPurchaseOrderId(poId);
        boolean allReceived = true;

        for (ReceiveStockRequest.ReceivedLine line : req.lines()) {
            PosPurchaseOrderItem poItem = items.stream()
                    .filter(i -> i.getId().equals(line.itemId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("PO Item", line.itemId().toString()));

            poItem.receiveQty(line.qtyReceived());
            poItemRepo.save(poItem);

            // Add to stock
            if (poItem.getCatalogueItemId() != null) {
                addStock(tenantId, poItem.getCatalogueItemId(), line.qtyReceived(),
                        poId, "PURCHASE", "PO: " + po.getOrderNumber(), receivedBy);
            }

            if (!poItem.isFullyReceived()) allReceived = false;
        }

        if (allReceived) po.markReceived();
        else po.markPartiallyReceived();
        poRepo.save(po);

        return toPurchaseOrderResponse(po, poItemRepo.findByPurchaseOrderId(poId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void deductStock(TenantId tenantId, UUID catalogueItemId,
                              BigDecimal qty, UUID txnId, UUID userId) {
        stockRepo.findByTenantIdAndCatalogueItemId(tenantId, catalogueItemId)
                .ifPresent(stock -> {
                    BigDecimal before = stock.getQtyOnHand();
                    BigDecimal after  = before.subtract(qty);
                    stock.adjustQty(qty.negate());
                    stockRepo.save(stock);
                    movementRepo.save(PosStockMovement.create(
                            tenantId.getValue(), stock.getId(), "SALE",
                            qty.negate(), before, after,
                            "SALE", txnId, null, userId));
                });
    }

    private void addStock(TenantId tenantId, UUID catalogueItemId,
                           BigDecimal qty, UUID refId, String movementType,
                           String notes, UUID userId) {
        stockRepo.findByTenantIdAndCatalogueItemId(tenantId, catalogueItemId)
                .ifPresent(stock -> {
                    BigDecimal before = stock.getQtyOnHand();
                    BigDecimal after  = before.add(qty);
                    stock.adjustQty(qty);
                    stockRepo.save(stock);
                    movementRepo.save(PosStockMovement.create(
                            tenantId.getValue(), stock.getId(), movementType,
                            qty, before, after,
                            movementType, refId, notes, userId));
                });
    }

    private UUID postSaleJournal(TenantId tenantId, PosTransaction txn,
                                  BigDecimal subtotal, BigDecimal vat, BigDecimal total) {
        try {
            // Debit: cash (1010) or bank (1020) based on payment method
            String debitCode = "CASH".equals(txn.getPaymentMethod()) ? "1010" : "1020";
            UUID debitAccountId  = findAccountByCode(tenantId, debitCode);
            UUID salesAccountId  = findAccountByCode(tenantId, "4010");

            if (debitAccountId == null || salesAccountId == null) return null;

            UUID journalId    = UUID.randomUUID();
            String entryNumber = "POS-" + txn.getTransactionNumber();

            jdbc.update("""
                INSERT INTO acc_journal_entries
                (id, tenant_id, entry_number, entry_date, description, entry_type, status,
                 total_debit, total_credit, posted_at, created_at, updated_at)
                VALUES (?,?,?,?,?,'MANUAL','POSTED',?,?,NOW(),NOW(),NOW())
                """,
                journalId, tenantId.getValue(), entryNumber, LocalDate.now(),
                "POS Sale: " + txn.getTransactionNumber(), total, total);

            // Debit cash/bank
            jdbc.update("""
                INSERT INTO acc_journal_lines
                (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
                VALUES (?,?,?,?,?,?,0,1,NOW())
                """,
                UUID.randomUUID(), tenantId.getValue(), journalId, debitAccountId,
                txn.getPaymentMethod() + " received", total);

            // Credit sales
            jdbc.update("""
                INSERT INTO acc_journal_lines
                (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
                VALUES (?,?,?,?,?,0,?,2,NOW())
                """,
                UUID.randomUUID(), tenantId.getValue(), journalId, salesAccountId,
                "Sales revenue", subtotal);

            return journalId;
        } catch (Exception e) {
            log.error("Failed to post POS journal: {}", e.getMessage());
            return null;
        }
    }

    private UUID findAccountByCode(TenantId tenantId, String code) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM acc_accounts WHERE tenant_id = ? AND account_code = ? AND active = true LIMIT 1",
                    UUID.class, tenantId.getValue(), code);
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> fetchCatalogueItem(UUID id) {
        try {
            return jdbc.queryForMap(
                    "SELECT name, default_price, vat_rate, sku, barcode FROM catalogue_items WHERE id = ? AND deleted_at IS NULL",
                    id);
        } catch (Exception e) { return null; }
    }

    private long countPendingOrders(TenantId tenantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pos_purchase_orders WHERE tenant_id = ? AND status IN ('ORDERED','PARTIALLY_RECEIVED')",
                    Long.class, tenantId.getValue());
        } catch (Exception e) { return 0; }
    }

    private StockItemResponse toStockResponse(PosStockItem s) {
        // Fetch catalogue details
        String itemName = null, sku = null, barcode = null;
        BigDecimal sellingPrice = BigDecimal.ZERO;
        try {
            var row = fetchCatalogueItem(s.getCatalogueItemId());
            if (row != null) {
                itemName     = (String) row.get("name");
                sku          = (String) row.get("sku");
                barcode      = (String) row.get("barcode");
                sellingPrice = (BigDecimal) row.get("default_price");
            }
        } catch (Exception ignored) {}

        return new StockItemResponse(s.getId(), s.getCatalogueItemId(),
                itemName, sku, barcode,
                s.getQtyOnHand(), s.getQtyReserved(), s.getAvailableQty(),
                s.getReorderLevel(), s.getReorderQty(),
                s.getCostPrice(), sellingPrice,
                s.getLocation(), s.isLowStock(), s.getUpdatedAt());
    }

    private TransactionResponse toTransactionResponse(PosTransaction t,
                                                       List<PosTransactionItem> items) {
        List<TransactionItemResponse> itemResponses = items.stream()
                .map(i -> new TransactionItemResponse(i.getId(), i.getCatalogueItemId(),
                        i.getItemName(), i.getSku(), i.getQty(), i.getUnitPrice(),
                        i.getVatRate(), i.getVatAmount(), i.getDiscountPct(),
                        i.getDiscountAmount(), i.getLineTotal()))
                .toList();
        return new TransactionResponse(t.getId(), t.getTransactionNumber(),
                t.getCustomerId(), t.getCustomerName(),
                t.getSubtotal(), t.getVatAmount(), t.getDiscountAmount(), t.getTotalAmount(),
                t.getPaymentMethod(), t.getAmountTendered(), t.getChangeGiven(),
                t.getPaymentRef(), t.getStatus(), t.getServedByName(),
                itemResponses, t.getCreatedAt());
    }

    private PurchaseOrderResponse toPurchaseOrderResponse(PosPurchaseOrder po,
                                                           List<PosPurchaseOrderItem> items) {
        List<PurchaseOrderItemResponse> itemResponses = items.stream()
                .map(i -> new PurchaseOrderItemResponse(i.getId(), i.getCatalogueItemId(),
                        i.getItemName(), i.getQtyOrdered(), i.getQtyReceived(),
                        i.getUnitCost(), i.getVatRate(), i.getLineTotal(),
                        i.isFullyReceived()))
                .toList();
        return new PurchaseOrderResponse(po.getId(), po.getOrderNumber(),
                po.getSupplierId(), po.getSupplierName(),
                po.getStatus(), po.getOrderDate(), po.getExpectedDate(), po.getReceivedDate(),
                po.getSubtotal(), po.getVatAmount(), po.getTotalAmount(),
                po.getNotes(), itemResponses, po.getCreatedAt());
    }
}
