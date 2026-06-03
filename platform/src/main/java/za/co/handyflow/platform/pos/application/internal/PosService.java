package za.co.handyflow.platform.pos.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.pos.domain.model.*;
import za.co.handyflow.platform.pos.domain.repository.*;
import za.co.handyflow.platform.pos.dto.*;
import za.co.handyflow.platform.shared.BusinessException;
import za.co.handyflow.platform.shared.NotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosService {

    private static final BigDecimal VAT_RATE_STANDARD = BigDecimal.valueOf(15);
    private static final BigDecimal VAT_DIVISOR       = BigDecimal.valueOf(100);
    private static final ZoneId     ZONE_SA           = ZoneId.of("Africa/Johannesburg");

    private final PosTransactionRepository       transactionRepo;
    private final PosTransactionItemRepository   transactionItemRepo;
    private final PosStockItemRepository         stockItemRepo;
    private final PosStockMovementRepository     movementRepo;
    private final PosPurchaseOrderRepository     purchaseOrderRepo;
    private final PosPurchaseOrderItemRepository purchaseOrderItemRepo;
    private final PosCashSessionRepository       cashSessionRepo;
    private final PosStockAdjustmentRepository   adjustmentRepo;
    private final PosStockAdjustmentItemRepository adjustmentItemRepo;
    private final CatalogueItemRepository        catalogueItemRepo;
    private final ObjectMapper                   objectMapper;

    // ═══════════════════════════════════════════════════════════════════════════
    // Summary
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public PosSummaryResponse getSummary(TenantId tenantId) {
        ZonedDateTime startOfDay   = LocalDate.now(ZONE_SA).atStartOfDay(ZONE_SA);
        ZonedDateTime startOfMonth = LocalDate.now(ZONE_SA).withDayOfMonth(1).atStartOfDay(ZONE_SA);
        ZonedDateTime now          = ZonedDateTime.now(ZONE_SA);

        Instant dayFrom   = startOfDay.toInstant();
        Instant monthFrom = startOfMonth.toInstant();
        Instant to        = now.toInstant();

        BigDecimal salesToday      = transactionRepo.sumSalesBetween(tenantId, dayFrom, to);
        BigDecimal salesThisMonth  = transactionRepo.sumSalesBetween(tenantId, monthFrom, to);
        long txToday               = transactionRepo.countSalesBetween(tenantId, dayFrom, to);
        long txMonth               = transactionRepo.countSalesBetween(tenantId, monthFrom, to);
        long totalStockItems       = stockItemRepo.count();
        long lowStockItems         = stockItemRepo.countLowStock(tenantId);
        long pendingOrders         = purchaseOrderRepo.findAll(tenantId, Pageable.unpaged())
                .stream().filter(p -> "ORDERED".equals(p.getStatus()) || "PARTIALLY_RECEIVED".equals(p.getStatus()))
                .count();

        return new PosSummaryResponse(salesToday, salesThisMonth,
                txToday, txMonth, totalStockItems, lowStockItems, pendingOrders);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cash Sessions
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public CashSessionResponse openCashSession(TenantId tenantId, UUID userId,
                                               String userName, OpenCashSessionRequest req) {
        // Only one open session at a time
        cashSessionRepo.findOpenSession(tenantId).ifPresent(existing -> {
            throw new BusinessException("A cash session is already open: " + existing.getSessionNumber()
                    + ". Close it before opening a new one.");
        });

        int seq     = cashSessionRepo.findMaxSessionSequence(tenantId) + 1;
        String num  = "SES" + String.format("%05d", seq);

        PosCashSession session = PosCashSession.open(tenantId, num, userId, userName,
                req.openingFloat(), req.notes());
        cashSessionRepo.save(session);
        log.info("[POS] Cash session {} opened by {} with float {}", num, userName, req.openingFloat());
        return mapSession(session);
    }

    @Transactional
    public CashSessionResponse closeCashSession(TenantId tenantId, UUID sessionId,
                                                UUID userId, String userName,
                                                CloseCashSessionRequest req) {
        PosCashSession session = cashSessionRepo.findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new NotFoundException("Cash session not found"));

        if (session.isClosed()) {
            throw new BusinessException("Session " + session.getSessionNumber() + " is already closed");
        }

        BigDecimal expectedCash    = transactionRepo.sumCashSalesBySession(sessionId);
        BigDecimal totalSales      = transactionRepo.sumTotalSalesBySession(sessionId);
        int        txCount         = transactionRepo.countBySession(sessionId);

        session.close(userId, userName, req.closingFloat(), expectedCash,
                totalSales, txCount, req.notes());
        cashSessionRepo.save(session);
        log.info("[POS] Session {} closed. Variance: {}", session.getSessionNumber(), session.getCashVariance());
        return mapSession(session);
    }

    @Transactional(readOnly = true)
    public Optional<CashSessionResponse> getOpenSession(TenantId tenantId) {
        return cashSessionRepo.findOpenSession(tenantId).map(this::mapSession);
    }

    @Transactional(readOnly = true)
    public Page<CashSessionResponse> getSessions(TenantId tenantId, Pageable pageable) {
        return cashSessionRepo.findAll(tenantId, pageable).map(this::mapSession);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POS Terminal — Process Sale
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public TransactionResponse processSale(TenantId tenantId, UUID userId,
                                           String staffName, ProcessSaleRequest req) {
        // ── Resolve cash session ───────────────────────────────────────────────
        UUID sessionId = null;
        boolean isCashInvolved = "CASH".equals(req.paymentMethod())
                || (req.splitPayments() != null && req.splitPayments().stream()
                .anyMatch(s -> "CASH".equals(s.paymentMethod())));

        if (isCashInvolved) {
            PosCashSession session = cashSessionRepo.findOpenSession(tenantId)
                    .orElseThrow(() -> new BusinessException(
                            "No open cash session. Open a cash session before processing CASH sales."));
            sessionId = session.getId();
        }

        // ── Validate and deduct stock ──────────────────────────────────────────
        List<StockDeduction> deductions = new ArrayList<>();
        for (ProcessSaleRequest.SaleLineItem line : req.items()) {
            if (line.catalogueItemId() == null) continue; // custom open item, no stock tracking
            PosStockItem stockItem = stockItemRepo
                    .findByTenantIdAndCatalogueItemId(tenantId, line.catalogueItemId())
                    .orElse(null);
            if (stockItem != null && stockItem.isTrackStock()) {
                BigDecimal available = stockItem.getAvailableQty();
                if (available.compareTo(line.qty()) < 0) {
                    throw new BusinessException(
                            "Insufficient stock for '" + getCatalogueItemName(tenantId, line.catalogueItemId())
                                    + "'. Available: " + available.stripTrailingZeros().toPlainString()
                                    + ", requested: " + line.qty().stripTrailingZeros().toPlainString());
                }
                deductions.add(new StockDeduction(stockItem, line.qty()));
            }
        }

        // ── Build transaction ──────────────────────────────────────────────────
        int    seq    = transactionRepo.findMaxTransactionSequence(tenantId) + 1;
        String txnNum = "POS-" + String.format("%06d", seq);

        String splitJson = serialiseSplitPayments(req.splitPayments());

        PosTransaction txn = PosTransaction.create(
                tenantId, txnNum,
                req.customerId(), req.customerName(),
                req.paymentMethod(),
                req.amountTendered(),
                req.paymentRef(),
                splitJson,
                sessionId,
                userId, staffName,
                req.notes());
        transactionRepo.save(txn);

        // ── Build line items and compute totals ────────────────────────────────
        BigDecimal subtotal        = BigDecimal.ZERO;
        BigDecimal totalVat        = BigDecimal.ZERO;
        BigDecimal totalDiscount   = BigDecimal.ZERO;
        List<PosTransactionItem> savedItems = new ArrayList<>();

        for (ProcessSaleRequest.SaleLineItem line : req.items()) {
            BigDecimal vatRate = resolveVatRate(tenantId, line.catalogueItemId());
            String itemName    = resolveItemName(tenantId, line.catalogueItemId(), line.itemName());
            String sku         = resolveItemSku(tenantId, line.catalogueItemId());

            PosTransactionItem item = PosTransactionItem.create(
                    txn.getId(), tenantId.getValue(),
                    line.catalogueItemId(), itemName, sku,
                    line.qty(), line.unitPrice(),
                    vatRate, line.discountPct());
            transactionItemRepo.save(item);
            savedItems.add(item);

            subtotal      = subtotal.add(item.getUnitPrice().multiply(item.getQty()));
            totalVat      = totalVat.add(item.getVatAmount());
            totalDiscount = totalDiscount.add(item.getDiscountAmount());
        }

        // Apply transaction-level discount (e.g. loyalty) on top of line discounts
        BigDecimal txnDiscountAmt = BigDecimal.ZERO;
        if (req.transactionDiscountPct() != null && req.transactionDiscountPct().compareTo(BigDecimal.ZERO) > 0) {
            txnDiscountAmt = subtotal.subtract(totalDiscount)
                    .multiply(req.transactionDiscountPct())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        totalDiscount = totalDiscount.add(txnDiscountAmt);

        BigDecimal netBeforeVat = subtotal.subtract(totalDiscount);
        BigDecimal vatOnNet     = netBeforeVat.multiply(VAT_RATE_STANDARD)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        // Use item-level VAT already computed; txn-level discount reduces it proportionally
        BigDecimal totalAmount  = savedItems.stream()
                .map(PosTransactionItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(txnDiscountAmt);

        txn.setTotals(subtotal, totalVat, totalDiscount, totalAmount);
        transactionRepo.save(txn);

        // ── Validate cash tendered ─────────────────────────────────────────────
        if ("CASH".equals(req.paymentMethod())) {
            if (req.amountTendered() == null || req.amountTendered().compareTo(totalAmount) < 0) {
                throw new BusinessException("Amount tendered must be >= total amount " + totalAmount);
            }
        }
        if ("SPLIT".equals(req.paymentMethod()) && req.splitPayments() != null) {
            BigDecimal splitTotal = req.splitPayments().stream()
                    .map(SplitPaymentLine::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (splitTotal.compareTo(totalAmount) != 0) {
                throw new BusinessException("Split payment amounts sum to " + splitTotal
                        + " but total is " + totalAmount + ". They must match exactly.");
            }
        }

        // ── Apply stock deductions ─────────────────────────────────────────────
        for (StockDeduction d : deductions) {
            BigDecimal qtyBefore = d.stockItem().getQtyOnHand();
            d.stockItem().adjustQty(d.qty().negate());
            stockItemRepo.save(d.stockItem());

            PosStockMovement movement = PosStockMovement.create(
                    tenantId.getValue(), d.stockItem().getId(),
                    "SALE", d.qty().negate(),
                    qtyBefore, d.stockItem().getQtyOnHand(),
                    "SALE", txn.getId(),
                    "POS Sale " + txnNum, userId);
            movementRepo.save(movement);
        }

        log.info("[POS] Sale {} completed. Total: {} {}", txnNum, totalAmount, req.paymentMethod());
        return mapTransaction(txn, savedItems);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Refunds
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public TransactionResponse processRefund(TenantId tenantId, UUID originalTxnId,
                                             UUID userId, String staffName,
                                             ProcessRefundRequest req) {
        PosTransaction original = transactionRepo.findByIdAndTenantId(originalTxnId, tenantId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        if (original.isVoided()) {
            throw new BusinessException("Cannot refund a voided transaction");
        }

        List<PosTransactionItem> originalItems = transactionItemRepo.findByTransactionId(originalTxnId);

        // Validate refund lines against original
        Map<UUID, PosTransactionItem> originalItemMap = originalItems.stream()
                .collect(Collectors.toMap(PosTransactionItem::getId, i -> i));

        for (ProcessRefundRequest.RefundLine line : req.items()) {
            PosTransactionItem orig = originalItemMap.get(line.transactionItemId());
            if (orig == null) {
                throw new BusinessException("Item " + line.transactionItemId()
                        + " does not belong to transaction " + original.getTransactionNumber());
            }
            // Check cumulative already-refunded qty
            BigDecimal alreadyRefunded = getAlreadyRefundedQty(tenantId, original.getId(),
                    line.transactionItemId());
            BigDecimal maxRefundable   = orig.getQty().subtract(alreadyRefunded);
            if (line.qtyReturned().compareTo(maxRefundable) > 0) {
                throw new BusinessException("Cannot refund " + line.qtyReturned() + " of '"
                        + orig.getItemName() + "'. Max refundable: " + maxRefundable);
            }
        }

        // Resolve refund method
        String refundMethod = req.refundMethod() != null ? req.refundMethod() : original.getPaymentMethod();

        // Cash session for cash refunds
        UUID sessionId = null;
        if ("CASH".equals(refundMethod)) {
            sessionId = cashSessionRepo.findOpenSession(tenantId)
                    .map(s -> s.getId()).orElse(null);
        }

        int    seq    = transactionRepo.findMaxTransactionSequence(tenantId) + 1;
        String refNum = "REF-" + String.format("%06d", seq);

        PosTransaction refund = PosTransaction.createRefund(
                tenantId, refNum, originalTxnId, req.reason(),
                original.getCustomerId(), original.getCustomerName(),
                refundMethod, sessionId, userId, staffName);
        transactionRepo.save(refund);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        List<PosTransactionItem> refundItems = new ArrayList<>();

        for (ProcessRefundRequest.RefundLine line : req.items()) {
            PosTransactionItem orig = originalItemMap.get(line.transactionItemId());

            // Refund item reuses original unit price, vat rate, discount
            PosTransactionItem refundItem = PosTransactionItem.create(
                    refund.getId(), tenantId.getValue(),
                    orig.getCatalogueItemId(), orig.getItemName(), orig.getSku(),
                    line.qtyReturned(), orig.getUnitPrice(),
                    orig.getVatRate(), orig.getDiscountPct());
            transactionItemRepo.save(refundItem);
            refundItems.add(refundItem);

            subtotal = subtotal.add(refundItem.getUnitPrice().multiply(refundItem.getQty()));
            totalVat = totalVat.add(refundItem.getVatAmount());

            // Return stock
            if (orig.getCatalogueItemId() != null) {
                stockItemRepo.findByTenantIdAndCatalogueItemId(tenantId, orig.getCatalogueItemId())
                        .ifPresent(stockItem -> {
                            BigDecimal qtyBefore = stockItem.getQtyOnHand();
                            stockItem.adjustQty(line.qtyReturned());
                            stockItemRepo.save(stockItem);
                            PosStockMovement movement = PosStockMovement.create(
                                    tenantId.getValue(), stockItem.getId(),
                                    "RETURN", line.qtyReturned(),
                                    qtyBefore, stockItem.getQtyOnHand(),
                                    "REFUND", refund.getId(),
                                    "Refund " + refNum + " against " + original.getTransactionNumber(),
                                    userId);
                            movementRepo.save(movement);
                        });
            }
        }

        BigDecimal totalAmount = refundItems.stream()
                .map(PosTransactionItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        refund.setTotals(subtotal, totalVat, BigDecimal.ZERO, totalAmount);
        transactionRepo.save(refund);

        // Mark original as REFUNDED if fully refunded
        original.markRefunded();
        transactionRepo.save(original);

        log.info("[POS] Refund {} processed against {}. Amount: {}", refNum,
                original.getTransactionNumber(), totalAmount);
        return mapTransaction(refund, refundItems);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Transactions
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(TenantId tenantId, Pageable pageable) {
        return transactionRepo.findAll(tenantId, pageable).map(t -> {
            List<PosTransactionItem> items = transactionItemRepo.findByTransactionId(t.getId());
            return mapTransaction(t, items);
        });
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(TenantId tenantId, UUID id) {
        PosTransaction txn = transactionRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Transaction " + id + " not found"));
        List<PosTransactionItem> items = transactionItemRepo.findByTransactionId(id);
        return mapTransaction(txn, items);
    }

    @Transactional
    public TransactionResponse voidTransaction(TenantId tenantId, UUID id,
                                               VoidTransactionRequest req, UUID userId) {
        PosTransaction txn = transactionRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        if (txn.isVoided()) {
            throw new BusinessException("Transaction " + txn.getTransactionNumber() + " is already voided");
        }

        // Reverse stock movements
        List<PosTransactionItem> items = transactionItemRepo.findByTransactionId(id);
        for (PosTransactionItem item : items) {
            if (item.getCatalogueItemId() == null) continue;
            stockItemRepo.findByTenantIdAndCatalogueItemId(tenantId, item.getCatalogueItemId())
                    .ifPresent(stockItem -> {
                        BigDecimal qtyBefore = stockItem.getQtyOnHand();
                        stockItem.adjustQty(item.getQty()); // restore
                        stockItemRepo.save(stockItem);
                        PosStockMovement movement = PosStockMovement.create(
                                tenantId.getValue(), stockItem.getId(),
                                "RETURN", item.getQty(),
                                qtyBefore, stockItem.getQtyOnHand(),
                                "VOID", id,
                                "Void of " + txn.getTransactionNumber() + ": " + req.reason(),
                                userId);
                        movementRepo.save(movement);
                    });
        }

        txn.voidTransaction(req.reason());
        transactionRepo.save(txn);
        log.info("[POS] Transaction {} voided by {}. Reason: {}", txn.getTransactionNumber(), userId, req.reason());
        return mapTransaction(txn, items);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Receipt
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(TenantId tenantId, UUID transactionId) {
        PosTransaction txn = transactionRepo.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
        List<PosTransactionItem> items = transactionItemRepo.findByTransactionId(transactionId);

        // Tenant info — pulled from tenant profile in production; using placeholders here
        // In production: inject TenantProfileService and call tenantProfileService.getProfile(tenantId)
        String tenantName    = "HandyFlow Business";
        String tenantAddress = "";
        String tenantPhone   = "";
        String tenantVatNum  = null; // populate from tenant profile when available

        List<ReceiptResponse.ReceiptLineItem> lineItems = items.stream().map(i ->
                new ReceiptResponse.ReceiptLineItem(
                        i.getItemName(), i.getSku(), i.getQty(), i.getUnitPrice(),
                        i.getDiscountPct(), i.getVatRate(), i.getLineTotal())
        ).toList();

        String html = buildHtmlReceipt(txn, items, tenantName, tenantVatNum);

        return new ReceiptResponse(
                tenantName, tenantAddress, tenantPhone, tenantVatNum,
                txn.getTransactionNumber(), txn.getCreatedAt(),
                txn.getServedByName(), txn.getCustomerName(),
                lineItems,
                txn.getSubtotal(), txn.getVatAmount(), txn.getDiscountAmount(), txn.getTotalAmount(),
                txn.getPaymentMethod(), txn.getAmountTendered(), txn.getChangeGiven(),
                txn.getPaymentRef(),
                "Thank you for your business!",
                html);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Z-Report
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public ZReportResponse getZReport(TenantId tenantId, UUID sessionId) {
        PosCashSession session = cashSessionRepo.findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new NotFoundException("Cash session not found"));

        // Use session open/close times as the date range
        Instant from = session.getOpenedAt();
        Instant to   = session.isClosed() ? session.getClosedAt() : Instant.now();

        // Payment method breakdown
        List<Object[]> pmRows = transactionRepo.sumByPaymentMethodBetween(tenantId, from, to);
        List<ZReportResponse.PaymentMethodBreakdown> byPm = pmRows.stream().map(r ->
                new ZReportResponse.PaymentMethodBreakdown(
                        (String) r[0],
                        ((Number) r[1]).intValue(),
                        (BigDecimal) r[2])
        ).toList();

        // Top 10 items
        List<Object[]> topRows = transactionRepo.topItemsBetween(tenantId, from, to,
                PageRequest.of(0, 10));
        List<ZReportResponse.TopItem> topItems = topRows.stream().map(r ->
                new ZReportResponse.TopItem(
                        (String) r[0],
                        (BigDecimal) r[1],
                        (BigDecimal) r[2])
        ).toList();

        BigDecimal grossSales   = transactionRepo.sumSalesBetween(tenantId, from, to);
        long       txCount      = transactionRepo.countSalesBetween(tenantId, from, to);

        // Refunds
        BigDecimal totalRefunds = BigDecimal.ZERO; // sum of REFUND transactions in session
        long       refundCount  = 0;

        BigDecimal expectedCash = session.isClosed()
                ? session.getExpectedCash()
                : transactionRepo.sumCashSalesBySession(sessionId);

        return new ZReportResponse(
                session.getId(), session.getSessionNumber(),
                session.getOpenedAt().atZone(ZONE_SA).toLocalDate(),
                session.getOpenedByName(), session.getClosedByName(),
                session.getOpenedAt(), session.getClosedAt(),
                grossSales,
                BigDecimal.ZERO,  // totalVat — computed from transactions if needed
                BigDecimal.ZERO,  // totalDiscount
                grossSales,       // netSales
                (int) txCount,
                (int) refundCount,
                totalRefunds,
                session.getOpeningFloat(),
                expectedCash,
                session.getClosingFloat(),
                session.getCashVariance(),
                byPm,
                topItems);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Stock Items
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<StockItemResponse> getStockItems(TenantId tenantId, Pageable pageable) {
        return stockItemRepo.findAll(tenantId, pageable).map(s -> mapStockItem(tenantId, s));
    }

    @Transactional(readOnly = true)
    public List<StockItemResponse> getLowStockItems(TenantId tenantId) {
        return stockItemRepo.findLowStock(tenantId).stream()
                .map(s -> mapStockItem(tenantId, s))
                .toList();
    }

    @Transactional
    public StockItemResponse createStockItem(TenantId tenantId, CreateStockItemRequest req) {
        stockItemRepo.findByTenantIdAndCatalogueItemId(tenantId, req.catalogueItemId())
                .ifPresent(existing -> {
                    throw new BusinessException("Stock item already exists for this catalogue item");
                });

        PosStockItem item = PosStockItem.create(
                tenantId, req.catalogueItemId(),
                req.qtyOnHand(), req.reorderLevel(),
                req.reorderQty(), req.costPrice(), req.location());
        stockItemRepo.save(item);

        // Record opening stock movement if qty > 0
        if (req.qtyOnHand() != null && req.qtyOnHand().compareTo(BigDecimal.ZERO) > 0) {
            PosStockMovement movement = PosStockMovement.create(
                    tenantId.getValue(), item.getId(),
                    "OPENING", req.qtyOnHand(),
                    BigDecimal.ZERO, req.qtyOnHand(),
                    null, null, "Opening stock", null);
            movementRepo.save(movement);
        }

        return mapStockItem(tenantId, item);
    }

    @Transactional
    public StockItemResponse updateStockItem(TenantId tenantId, UUID id, UpdateStockItemRequest req) {
        PosStockItem item = stockItemRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Stock item not found"));
        item.update(req.reorderLevel(), req.reorderQty(), req.costPrice(), req.location());
        stockItemRepo.save(item);
        return mapStockItem(tenantId, item);
    }

    @Transactional(readOnly = true)
    public StockItemResponse lookupByBarcode(TenantId tenantId, String barcode) {
        // Find catalogue item by barcode, then find stock item
        return catalogueItemRepo.findByTenantIdAndBarcode(tenantId.getValue(), barcode)
                .flatMap(cat -> stockItemRepo.findByTenantIdAndCatalogueItemId(tenantId, cat.getId()))
                .map(s -> mapStockItem(tenantId, s))
                .orElseThrow(() -> new NotFoundException("No stock item found for barcode: " + barcode));
    }

    @Transactional(readOnly = true)
    public List<PosStockMovement> getStockMovements(TenantId tenantId, UUID stockItemId) {
        stockItemRepo.findByIdAndTenantId(stockItemId, tenantId)
                .orElseThrow(() -> new NotFoundException("Stock item not found"));
        return movementRepo.findByStockItemIdOrderByCreatedAtDesc(stockItemId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Stock Adjustments
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public StockAdjustmentResponse createAdjustment(TenantId tenantId,
                                                    CreateStockAdjustmentRequest req,
                                                    UUID userId) {
        int    seq = adjustmentRepo.findMaxAdjustmentSequence(tenantId) + 1;
        String num = "ADJ" + String.format("%05d", seq);

        PosStockAdjustment adj = PosStockAdjustment.create(
                tenantId, num, req.reason(), req.notes(), userId);
        adjustmentRepo.save(adj);

        List<PosStockAdjustmentItem> lines = new ArrayList<>();
        for (CreateStockAdjustmentRequest.AdjustmentLine line : req.lines()) {
            PosStockItem stockItem = stockItemRepo.findByIdAndTenantId(line.stockItemId(), tenantId)
                    .orElseThrow(() -> new NotFoundException("Stock item not found: " + line.stockItemId()));

            PosStockAdjustmentItem adjItem = PosStockAdjustmentItem.create(
                    adj.getId(), stockItem.getId(),
                    stockItem.getQtyOnHand(), line.qtyActual());
            adjustmentItemRepo.save(adjItem);
            lines.add(adjItem);
        }

        return mapAdjustment(tenantId, adj, lines);
    }

    @Transactional
    public StockAdjustmentResponse applyAdjustment(TenantId tenantId, UUID adjustmentId, UUID userId) {
        PosStockAdjustment adj = adjustmentRepo.findByIdAndTenantId(adjustmentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Adjustment not found"));

        if (adj.isApplied()) {
            throw new BusinessException("Adjustment " + adj.getAdjustmentNumber() + " is already applied");
        }

        List<PosStockAdjustmentItem> lines = adjustmentItemRepo.findByAdjustmentId(adjustmentId);
        for (PosStockAdjustmentItem line : lines) {
            PosStockItem stockItem = stockItemRepo.findById(line.getStockItemId())
                    .orElseThrow(() -> new NotFoundException("Stock item not found"));

            BigDecimal qtyBefore = stockItem.getQtyOnHand();
            stockItem.setQty(line.getQtyActual());
            stockItemRepo.save(stockItem);

            if (line.getQtyDifference().compareTo(BigDecimal.ZERO) != 0) {
                String movType = adj.getReason().equals("WASTE") || adj.getReason().equals("DAMAGE")
                        || adj.getReason().equals("EXPIRY") ? "WASTE" : "ADJUSTMENT";
                PosStockMovement movement = PosStockMovement.create(
                        tenantId.getValue(), stockItem.getId(),
                        movType, line.getQtyDifference(),
                        qtyBefore, line.getQtyActual(),
                        "ADJUSTMENT", adjustmentId,
                        adj.getReason() + ": " + (adj.getNotes() != null ? adj.getNotes() : ""),
                        userId);
                movementRepo.save(movement);
            }
        }

        adj.apply(userId);
        adjustmentRepo.save(adj);
        log.info("[POS] Stock adjustment {} applied by {}", adj.getAdjustmentNumber(), userId);
        return mapAdjustment(tenantId, adj, lines);
    }

    @Transactional(readOnly = true)
    public Page<StockAdjustmentResponse> getAdjustments(TenantId tenantId, Pageable pageable) {
        return adjustmentRepo.findAll(tenantId, pageable).map(adj -> {
            List<PosStockAdjustmentItem> lines = adjustmentItemRepo.findByAdjustmentId(adj.getId());
            return mapAdjustment(tenantId, adj, lines);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Purchase Orders
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> getPurchaseOrders(TenantId tenantId, Pageable pageable) {
        return purchaseOrderRepo.findAll(tenantId, pageable).map(po -> {
            List<PosPurchaseOrderItem> items = purchaseOrderItemRepo.findByPurchaseOrderId(po.getId());
            return mapPurchaseOrder(po, items);
        });
    }

    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(TenantId tenantId, UUID userId,
                                                     CreatePurchaseOrderRequest req) {
        int    seq = purchaseOrderRepo.findMaxOrderSequence(tenantId) + 1;
        String num = "PO-" + String.format("%05d", seq);

        PosPurchaseOrder po = PosPurchaseOrder.create(
                tenantId, num, req.supplierId(), req.supplierName(),
                req.expectedDate(), req.notes(), userId);
        purchaseOrderRepo.save(po);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        List<PosPurchaseOrderItem> items = new ArrayList<>();

        for (CreatePurchaseOrderRequest.PurchaseOrderLine line : req.items()) {
            String itemName = resolveItemName(tenantId, line.catalogueItemId(), line.itemName());
            PosPurchaseOrderItem poItem = PosPurchaseOrderItem.create(
                    po.getId(), tenantId.getValue(),
                    line.catalogueItemId(), itemName,
                    line.qtyOrdered(), line.unitCost(), line.vatRate());
            purchaseOrderItemRepo.save(poItem);
            items.add(poItem);

            BigDecimal lineSub = line.unitCost().multiply(line.qtyOrdered());
            BigDecimal vat     = lineSub.multiply(poItem.getVatRate())
                    .divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineSub);
            vatTotal = vatTotal.add(vat);
        }

        po.setTotals(subtotal, vatTotal, subtotal.add(vatTotal));
        po.markOrdered();
        purchaseOrderRepo.save(po);

        return mapPurchaseOrder(po, items);
    }

    @Transactional
    public PurchaseOrderResponse receiveStock(TenantId tenantId, UUID poId,
                                              ReceiveStockRequest req, UUID userId) {
        PosPurchaseOrder po = purchaseOrderRepo.findByIdAndTenantId(poId, tenantId)
                .orElseThrow(() -> new NotFoundException("Purchase order not found"));

        if (!po.isOrdered()) {
            throw new BusinessException("Purchase order " + po.getOrderNumber()
                    + " cannot receive stock in status: " + po.getStatus());
        }

        List<PosPurchaseOrderItem> poItems = purchaseOrderItemRepo.findByPurchaseOrderId(poId);
        Map<UUID, PosPurchaseOrderItem> poItemMap = poItems.stream()
                .collect(Collectors.toMap(PosPurchaseOrderItem::getId, i -> i));

        for (ReceiveStockRequest.ReceivedLine line : req.lines()) {
            PosPurchaseOrderItem poItem = poItemMap.get(line.itemId());
            if (poItem == null) continue;

            poItem.receiveQty(line.qtyReceived());
            purchaseOrderItemRepo.save(poItem);

            if (poItem.getCatalogueItemId() != null) {
                PosStockItem stockItem = stockItemRepo
                        .findByTenantIdAndCatalogueItemId(tenantId, poItem.getCatalogueItemId())
                        .orElse(null);
                if (stockItem != null) {
                    BigDecimal qtyBefore = stockItem.getQtyOnHand();
                    stockItem.adjustQty(line.qtyReceived());
                    stockItem.updateCostPrice(poItem.getUnitCost()); // update to latest cost
                    stockItemRepo.save(stockItem);

                    PosStockMovement movement = PosStockMovement.create(
                            tenantId.getValue(), stockItem.getId(),
                            "PURCHASE", line.qtyReceived(),
                            qtyBefore, stockItem.getQtyOnHand(),
                            "PURCHASE_ORDER", poId,
                            "Received against PO " + po.getOrderNumber(), userId);
                    movementRepo.save(movement);
                }
            }
        }

        // Update PO status
        boolean allReceived = poItems.stream().allMatch(PosPurchaseOrderItem::isFullyReceived);
        if (allReceived) {
            po.markReceived();
        } else {
            po.markPartiallyReceived();
        }
        purchaseOrderRepo.save(po);

        return mapPurchaseOrder(po, poItems);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private BigDecimal resolveVatRate(TenantId tenantId, UUID catalogueItemId) {
        if (catalogueItemId == null) return VAT_RATE_STANDARD;
        return catalogueItemRepo.findById(catalogueItemId)
                .map(cat -> {
                    // If catalogue item has vatExempt flag, return 0, else 15
                    try {
                        var vatExempt = cat.getClass().getMethod("isVatExempt").invoke(cat);
                        return Boolean.TRUE.equals(vatExempt) ? BigDecimal.ZERO : VAT_RATE_STANDARD;
                    } catch (Exception e) {
                        return VAT_RATE_STANDARD;
                    }
                })
                .orElse(VAT_RATE_STANDARD);
    }

    private String resolveItemName(TenantId tenantId, UUID catalogueItemId, String fallback) {
        if (catalogueItemId == null) return fallback != null ? fallback : "Custom Item";
        return catalogueItemRepo.findById(catalogueItemId)
                .map(cat -> {
                    try { return (String) cat.getClass().getMethod("getName").invoke(cat); }
                    catch (Exception e) { return fallback; }
                })
                .orElse(fallback != null ? fallback : "Unknown Item");
    }

    private String resolveItemSku(TenantId tenantId, UUID catalogueItemId) {
        if (catalogueItemId == null) return null;
        return catalogueItemRepo.findById(catalogueItemId)
                .map(cat -> {
                    try { return (String) cat.getClass().getMethod("getSku").invoke(cat); }
                    catch (Exception e) { return null; }
                })
                .orElse(null);
    }

    private String getCatalogueItemName(TenantId tenantId, UUID catalogueItemId) {
        return resolveItemName(tenantId, catalogueItemId, "item " + catalogueItemId);
    }

    private BigDecimal getAlreadyRefundedQty(TenantId tenantId, UUID originalTxnId,
                                             UUID itemId) {
        return transactionRepo.findByOriginalTransactionIdAndTenantId(originalTxnId, tenantId)
                .stream()
                .flatMap(refTxn -> transactionItemRepo.findByTransactionId(refTxn.getId()).stream())
                .filter(i -> i.getId().equals(itemId)
                        || (i.getCatalogueItemId() != null &&
                        transactionItemRepo.findByTransactionId(originalTxnId).stream()
                                .filter(oi -> oi.getId().equals(itemId))
                                .anyMatch(oi -> oi.getCatalogueItemId().equals(i.getCatalogueItemId()))))
                .map(PosTransactionItem::getQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String serialiseSplitPayments(List<SplitPaymentLine> splits) {
        if (splits == null || splits.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(splits);
        } catch (JsonProcessingException e) {
            log.warn("[POS] Failed to serialise split payments", e);
            return null;
        }
    }

    private List<SplitPaymentLine> deserialiseSplitPayments(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildHtmlReceipt(PosTransaction txn, List<PosTransactionItem> items,
                                    String tenantName, String vatNumber) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
                .withZone(ZONE_SA);

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:monospace;max-width:300px;margin:0 auto'>");
        sb.append("<div style='text-align:center'><strong>").append(tenantName).append("</strong><br/>");
        if (vatNumber != null) sb.append("VAT: ").append(vatNumber).append("<br/>");
        sb.append("</div><hr/>");
        sb.append("<div>TXN: ").append(txn.getTransactionNumber()).append("<br/>");
        sb.append("Date: ").append(fmt.format(txn.getCreatedAt())).append("<br/>");
        if (txn.getServedByName() != null) sb.append("Cashier: ").append(txn.getServedByName()).append("<br/>");
        if (txn.getCustomerName() != null) sb.append("Customer: ").append(txn.getCustomerName()).append("<br/>");
        sb.append("</div><hr/>");
        sb.append("<table width='100%'>");
        for (PosTransactionItem item : items) {
            sb.append("<tr><td>").append(item.getItemName()).append("</td>");
            sb.append("<td align='right'>").append(item.getQty()).append(" x ").append(item.getUnitPrice()).append("</td>");
            sb.append("<td align='right'>R ").append(item.getLineTotal()).append("</td></tr>");
        }
        sb.append("</table><hr/>");
        sb.append("<table width='100%'>");
        sb.append("<tr><td>Subtotal</td><td align='right'>R ").append(txn.getSubtotal()).append("</td></tr>");
        if (txn.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<tr><td>Discount</td><td align='right'>- R ").append(txn.getDiscountAmount()).append("</td></tr>");
        }
        sb.append("<tr><td>VAT (15%)</td><td align='right'>R ").append(txn.getVatAmount()).append("</td></tr>");
        sb.append("<tr><td><strong>TOTAL</strong></td><td align='right'><strong>R ")
                .append(txn.getTotalAmount()).append("</strong></td></tr>");
        if (txn.isCashPayment() && txn.getAmountTendered() != null) {
            sb.append("<tr><td>Cash</td><td align='right'>R ").append(txn.getAmountTendered()).append("</td></tr>");
            sb.append("<tr><td>Change</td><td align='right'>R ").append(txn.getChangeGiven()).append("</td></tr>");
        }
        sb.append("</table><hr/>");
        sb.append("<div style='text-align:center'>Thank you for your business!</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private CashSessionResponse mapSession(PosCashSession s) {
        return new CashSessionResponse(
                s.getId(), s.getSessionNumber(),
                s.getOpenedBy(), s.getOpenedByName(),
                s.getClosedBy(), s.getClosedByName(),
                s.getOpeningFloat(), s.getClosingFloat(),
                s.getExpectedCash(), s.getCashVariance(),
                s.getTotalSales(), s.getTransactionCount(),
                s.getStatus(), s.getNotes(),
                s.getOpenedAt(), s.getClosedAt());
    }

    private StockItemResponse mapStockItem(TenantId tenantId, PosStockItem s) {
        String itemName = resolveItemName(tenantId, s.getCatalogueItemId(), "Unknown");
        String sku      = resolveItemSku(tenantId, s.getCatalogueItemId());
        String barcode  = null; // resolve from catalogue if needed

        // Selling price comes from catalogue default_price
        BigDecimal sellingPrice = catalogueItemRepo.findById(s.getCatalogueItemId())
                .map(cat -> {
                    try { return (BigDecimal) cat.getClass().getMethod("getDefaultPrice").invoke(cat); }
                    catch (Exception e) { return BigDecimal.ZERO; }
                })
                .orElse(BigDecimal.ZERO);

        return new StockItemResponse(
                s.getId(), s.getCatalogueItemId(),
                itemName, sku, barcode,
                s.getQtyOnHand(), s.getQtyReserved(), s.getAvailableQty(),
                s.getReorderLevel(), s.getReorderQty(),
                s.getCostPrice(), sellingPrice,
                s.getLocation(), s.isLowStock(), s.getUpdatedAt());
    }

    private TransactionResponse mapTransaction(PosTransaction t, List<PosTransactionItem> items) {
        List<TransactionItemResponse> itemResponses = items.stream().map(i ->
                new TransactionItemResponse(
                        i.getId(), i.getCatalogueItemId(), i.getItemName(), i.getSku(),
                        i.getQty(), i.getUnitPrice(), i.getVatRate(), i.getVatAmount(),
                        i.getDiscountPct(), i.getDiscountAmount(), i.getLineTotal())
        ).toList();

        List<SplitPaymentLine> splitPayments = deserialiseSplitPayments(t.getSplitPaymentsJson());

        return new TransactionResponse(
                t.getId(), t.getTransactionNumber(),
                t.getCustomerId(), t.getCustomerName(),
                t.getSubtotal(), t.getVatAmount(), t.getDiscountAmount(), t.getTotalAmount(),
                t.getPaymentMethod(), t.getAmountTendered(), t.getChangeGiven(),
                t.getPaymentRef(), splitPayments,
                t.getStatus(), t.getVoidedReason(),
                t.getOriginalTransactionId(), t.getRefundReason(),
                t.getCashSessionId(), null,
                t.getServedBy(), t.getServedByName(),
                itemResponses, t.getCreatedAt());
    }

    private PurchaseOrderResponse mapPurchaseOrder(PosPurchaseOrder po,
                                                   List<PosPurchaseOrderItem> items) {
        List<PurchaseOrderItemResponse> itemResponses = items.stream().map(i ->
                new PurchaseOrderItemResponse(
                        i.getId(), i.getCatalogueItemId(), i.getItemName(),
                        i.getQtyOrdered(), i.getQtyReceived(),
                        i.getUnitCost(), i.getVatRate(), i.getLineTotal(),
                        i.isFullyReceived())
        ).toList();
        return new PurchaseOrderResponse(
                po.getId(), po.getOrderNumber(),
                po.getSupplierId(), po.getSupplierName(),
                po.getStatus(), po.getOrderDate(),
                po.getExpectedDate(), po.getReceivedDate(),
                po.getSubtotal(), po.getVatAmount(), po.getTotalAmount(),
                po.getNotes(), itemResponses, po.getCreatedAt());
    }

    private StockAdjustmentResponse mapAdjustment(TenantId tenantId,
                                                  PosStockAdjustment adj,
                                                  List<PosStockAdjustmentItem> lines) {
        List<StockAdjustmentResponse.AdjustmentLineResponse> lineResponses = lines.stream().map(l -> {
            String itemName = stockItemRepo.findById(l.getStockItemId())
                    .map(s -> resolveItemName(tenantId, s.getCatalogueItemId(), "Unknown"))
                    .orElse("Unknown");
            return new StockAdjustmentResponse.AdjustmentLineResponse(
                    l.getId(), l.getStockItemId(), itemName,
                    l.getQtySystem(), l.getQtyActual(), l.getQtyDifference());
        }).toList();

        return new StockAdjustmentResponse(
                adj.getId(), adj.getAdjustmentNumber(), adj.getReason(), adj.getNotes(),
                adj.getStatus(), lineResponses,
                adj.getCreatedBy(), adj.getAppliedBy(),
                adj.getCreatedAt(), adj.getAppliedAt());
    }

    // ── Inner record for stock deduction tracking ─────────────────────────────

    private record StockDeduction(PosStockItem stockItem, BigDecimal qty) {}
}
