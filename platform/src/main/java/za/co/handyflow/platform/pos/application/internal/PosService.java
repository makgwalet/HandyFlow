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
import za.co.handyflow.platform.catalogue.CatalogueFacade;
import za.co.handyflow.platform.catalogue.CatalogueItemSummary;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.pos.domain.model.*;
import za.co.handyflow.platform.pos.domain.repository.*;
import za.co.handyflow.platform.pos.dto.*;
import za.co.handyflow.platform.shared.BusinessException;
import za.co.handyflow.platform.shared.NotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;

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

    private static final BigDecimal VAT_DIVISOR       = BigDecimal.valueOf(100);
    private static final ZoneId     ZONE_SA           = ZoneId.of("Africa/Johannesburg");

    // FIX: backlog 1.6/10.1 — account codes for the batched session-close
    // posting. Real, confirmed seeded codes from ChartOfAccountsSeeder,
    // not invented. See postSessionSalesJournal()'s own Javadoc for the
    // full rationale (single clearing account, not per-payment-method
    // splitting; sales only, refunds excluded from this pass).
    private static final String POS_CASH_ACCOUNT_CODE    = "1010"; // Cash and Cash Equivalents
    private static final String POS_REVENUE_ACCOUNT_CODE = "4000"; // Revenue
    private static final String POS_VAT_ACCOUNT_CODE     = "2100"; // VAT Output (Payable)

    private final PosTransactionRepository       transactionRepo;
    private final PosTransactionItemRepository   transactionItemRepo;
    private final PosStockItemRepository         stockItemRepo;
    private final PosStockMovementRepository     movementRepo;
    private final PosPurchaseOrderRepository     purchaseOrderRepo;
    private final PosPurchaseOrderItemRepository purchaseOrderItemRepo;
    private final PosCashSessionRepository       cashSessionRepo;
    private final PosStockAdjustmentRepository   adjustmentRepo;
    private final PosStockAdjustmentItemRepository adjustmentItemRepo;
    private final CatalogueFacade catalogueFacade;
    private final ObjectMapper                   objectMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;
    private final PosSettingsService posSettingsService;
    // FIX: backlog 1.6/10.1 — posts a single batched sales journal at
    // cash-session close, instead of nothing reaching the ledger at all.
    // Direct AccountingFacade call (not an event, unlike Invoicing) —
    // confirmed no circular dependency: accounting does not depend on
    // pos, so pos depending on accounting is a clean one-directional
    // edge, same shape as AP's own migration.
    private final AccountingFacade accountingFacade;
    // FIX (VAT consolidation pass): replaces the private static final
    // BigDecimal.valueOf(15) literal that used to sit above the field
    // list — see VatRateProvider's own Javadoc for the full "scattered
    // in 4+ places" finding this closes. ratePercent() (15.00) matches
    // this class's own existing percentage-based arithmetic exactly, so
    // every call site below only needed its literal swapped, not its
    // math rewritten.
    private final za.co.handyflow.platform.shared.VatRateProvider vatRateProvider;

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
        // FIX: was stockItemRepo.count() — the raw, un-scoped JpaRepository
        // method, counting stock items across every tenant in the database.
        // Every other line in this method already correctly scopes by
        // tenantId; this one didn't, so "total stock items" on the dashboard
        // was showing the wrong number to every single tenant.
        long totalStockItems       = stockItemRepo.countByTenantId(tenantId);
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

        // FIX: backlog 1.6/10.1 — was previously nothing here; a whole
        // day's POS sales never reached the general ledger. Batched as
        // ONE journal entry per session close (not one per sale), per
        // your own confirmed decision on posting granularity. Sales
        // only — refunds are excluded from totalSales/vatTotal by the
        // same query filters every other session total already uses
        // (originalTransactionId IS NULL); refund netting into this
        // entry is a flagged follow-up, not silently included.
        // Debits a single "Cash and Cash Equivalents" (1010) clearing
        // account for the FULL total (cash+card+EFT combined) rather
        // than splitting per payment method — the actual bank deposit
        // (once card/EFT settlements land, cash is banked) is exactly
        // what Accounting's own existing bank-reconciliation feature
        // (reconcileTransaction/reconcileWithNewJournal) already exists
        // to match up later; this entry just correctly recognizes the
        // revenue and VAT liability at the moment of sale, which is the
        // actual gap backlog 1.6 identified.
        postSessionSalesJournal(tenantId, session);

        // FIX: backlog 10.2 — was calling BOTH an old unconditional
        // notification (fired on ANY nonzero variance, from before the
        // tolerance system existed) AND evaluateCashVariance() (the
        // real, tolerance-aware implementation, correctly using
        // PosSettings' configurable percentage/amount floor and
        // CRITICAL-tier escalation). Running both meant: any variance
        // WITHIN tolerance still notified anyway (the tolerance
        // suppression never actually worked), and any variance OUTSIDE
        // tolerance notified TWICE. This was leftover code from before
        // the real fix landed, never deleted — not a design gap needing
        // a decision, just dead/duplicate code removal.
        evaluateCashVariance(tenantId, session, userName);

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

        // FIX (VAT consolidation pass, adjacent cleanup): removed a dead
        // `netBeforeVat`/`vatOnNet` pair that used to sit here —
        // confirmed via search that netBeforeVat was computed only to
        // feed vatOnNet, and vatOnNet itself was never read anywhere in
        // this file; the very next line's own comment already says to
        // use the item-level `totalVat` accumulated above instead,
        // which is what actually flows into txn.setTotals() below.
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
            boolean wasLow = d.stockItem().isLowStock();
            d.stockItem().adjustQty(d.qty().negate());
            stockItemRepo.save(d.stockItem());

            PosStockMovement movement = PosStockMovement.create(
                    tenantId.getValue(), d.stockItem().getId(),
                    "SALE", d.qty().negate(),
                    qtyBefore, d.stockItem().getQtyOnHand(),
                    "SALE", txn.getId(),
                    "POS Sale " + txnNum, userId);
            movementRepo.save(movement);

            // NEW (Tier 1 gap analysis): edge-triggered — mirrors Fuel's
            // FUEL_TANK_LOW exactly. Fires only on the sale that crosses the
            // item from "not low" into "low", not on every sale while it
            // stays low, and not on an item that started low.
            if (!wasLow && d.stockItem().isLowStock()) {
                notifyLowStock(tenantId, d.stockItem());
            }
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

        // FIX: was original.markRefunded() called unconditionally — the
        // comment above it said "if fully refunded" but the code never
        // actually checked that condition. Refunding one item out of five
        // marked the entire transaction REFUNDED, misrepresenting a sale
        // that was 80% still legitimate and unreturned.
        //
        // Reuses getAlreadyRefundedQty() (already used above to validate
        // this refund's requested quantities) rather than introducing a
        // second way of computing the same thing — called here after the
        // new refund is saved, so its sum now includes this refund too.
        // Only marks REFUNDED once every original line item's cumulative
        // refunded quantity has caught up to what was originally sold.
        boolean fullyRefunded = originalItems.stream().allMatch(origItem ->
                getAlreadyRefundedQty(tenantId, original.getId(), origItem.getId())
                        .compareTo(origItem.getQty()) >= 0);
        if (fullyRefunded) {
            original.markRefunded();
            transactionRepo.save(original);
        }

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

        // FIX: was hardcoded literals — "HandyFlow Business" as the tenant
        // name (not the actual business's name), empty address/phone, null
        // VAT number, with a comment admitting these were placeholders.
        // Confirmed the frontend never even called this endpoint at all
        // (it built its own separate, simpler receipt inline instead) — so
        // fixing only the frontend to call this endpoint wouldn't have
        // helped; this endpoint itself needed fixing first.
        //
        // Now pulls the real tenants.name/phone/vat_number/address columns
        // (V9__tenant_company_details.sql — the same columns the Invoicing
        // module's PDF generation already relies on). address is JSONB
        // ({street, suburb, city, province, postalCode}, the same shape
        // used for CRM customer addresses elsewhere in this codebase) —
        // flattened into a single display line for the receipt.
        TenantProfile profile = fetchTenantProfile(tenantId);
        String tenantName    = profile.name();
        String tenantAddress = profile.address();
        String tenantPhone   = profile.phone();
        String tenantVatNum  = profile.vatNumber();

        List<ReceiptResponse.ReceiptLineItem> lineItems = items.stream().map(i ->
                new ReceiptResponse.ReceiptLineItem(
                        i.getItemName(), i.getSku(), i.getQty(), i.getUnitPrice(),
                        i.getDiscountPct(), i.getVatRate(), i.getLineTotal())
        ).toList();

        String html = buildHtmlReceipt(txn, items, tenantName, tenantAddress, tenantPhone, tenantVatNum);

        // FIX: vatAmount and discountAmount were passed in each other's
        // positional slots — the record declares (subtotal, discountAmount,
        // vatAmount, totalAmount) but this call passed (subtotal, VAT,
        // discount, total). Compiles fine since records take positional
        // args with no named-parameter checking, but every receipt showed
        // the real VAT amount labelled "Discount" and the real discount
        // amount labelled "VAT" — confirmed against a real receipt where a
        // R575 total (subtotal R500 + correct 15% VAT of R75, no discount
        // at all) displayed as "Discount -R75 / VAT R0", making the totals
        // look like they didn't add up when the underlying math was
        // actually correct throughout — only the two labels were swapped.
        return new ReceiptResponse(
                tenantName, tenantAddress, tenantPhone, tenantVatNum,
                txn.getTransactionNumber(), txn.getCreatedAt(),
                txn.getServedByName(), txn.getCustomerName(),
                lineItems,
                txn.getSubtotal(), txn.getDiscountAmount(), txn.getVatAmount(), txn.getTotalAmount(),
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
        Instant to   = session.isClosed() ?
                session.getClosedAt() : Instant.now();

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

        // FIX (HandyFlow BOS Discovery doc, Section 60/66): was
        // BigDecimal.ZERO hardcoded for totalVat/totalDiscount and
        // BigDecimal.ZERO/0 hardcoded for totalRefunds/refundCount,
        // despite PosTransaction already storing real per-transaction
        // vatAmount/discountAmount (see setTotals() calls in
        // processSale()/processRefund() above) — every VAT-registered
        // tenant's end-of-day report was showing zero VAT/discount
        // regardless of actual sales, and refunds never appeared at all.
        // Backed by two new aggregate queries on PosTransactionRepository
        // (sumVatAndDiscountBetween, sumRefundsBetween), same filter
        // shape as sumByPaymentMethodBetween/topItemsBetween above.
        Object[] vatAndDiscount = transactionRepo.sumVatAndDiscountBetween(tenantId, from, to);
        BigDecimal totalVat      = (BigDecimal) vatAndDiscount[0];
        BigDecimal totalDiscount = (BigDecimal) vatAndDiscount[1];

        Object[] refundData     = transactionRepo.sumRefundsBetween(tenantId, from, to);
        long       refundCount  = ((Number) refundData[0]).longValue();
        BigDecimal totalRefunds = (BigDecimal) refundData[1];

        BigDecimal expectedCash = session.isClosed()
                ? session.getExpectedCash()
                : transactionRepo.sumCashSalesBySession(sessionId);

        return new ZReportResponse(
                session.getId(), session.getSessionNumber(),
                session.getOpenedAt().atZone(ZONE_SA).toLocalDate(),
                session.getOpenedByName(), session.getClosedByName(),
                session.getOpenedAt(), session.getClosedAt(),
                grossSales,
                totalVat,
                totalDiscount,
                // FIX: netSales was hardcoded to grossSales — same bug
                // family as totalVat/totalDiscount/totalRefunds above,
                // just less visible since the field name doesn't say
                // "refunds". Now nets out real refunds for the period.
                grossSales.subtract(totalRefunds),
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
        return catalogueFacade.findItemByBarcode(tenantId, barcode)
                .flatMap(item -> stockItemRepo.findByTenantIdAndCatalogueItemId(tenantId, item.id()))
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
            // FIX (VAT consolidation pass): line.vatRate() is genuinely
            // nullable at the DTO level (no validation constrains it —
            // confirmed directly against CreatePurchaseOrderRequest) and
            // was previously passed straight through to
            // PosPurchaseOrderItem.create(), whose own internal fallback
            // (a hardcoded BigDecimal.valueOf(15)) was the only thing
            // resolving it — unlike CatalogueItem's and
            // PosTransactionItem's equivalent entity-level fallbacks,
            // this one was actually reachable via a real, unprotected
            // call path, not dead defensive code. Resolved here instead,
            // matching the same pattern CatalogueService.createItem()/
            // updateItem() already use — the entity's own fallback stays
            // in place as a backstop, now genuinely unreachable too.
            BigDecimal poVatRate = line.vatRate() != null ? line.vatRate() : vatRateProvider.ratePercent();
            PosPurchaseOrderItem poItem = PosPurchaseOrderItem.create(
                    po.getId(), tenantId.getValue(),
                    line.catalogueItemId(), itemName,
                    line.qtyOrdered(), line.unitCost(), poVatRate);
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

    /**
     * FIX: backlog 1.6/10.1. See closeCashSession()'s own call-site
     * comment for the full rationale (batched-per-session, sales-only,
     * single clearing account rather than per-payment-method splitting).
     * This was the piece that was missing — the call site landed, this
     * method definition didn't, which would have been a compile error.
     */
    private void postSessionSalesJournal(TenantId tenantId, PosCashSession session) {
        BigDecimal totalSales = transactionRepo.sumTotalSalesBySession(session.getId());
        if (totalSales == null || totalSales.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("[POS] Session {} had no sales — nothing to post", session.getSessionNumber());
            return;
        }
        BigDecimal vatTotal = transactionRepo.sumVatBySession(session.getId());
        if (vatTotal == null) vatTotal = BigDecimal.ZERO;
        BigDecimal subtotal = totalSales.subtract(vatTotal);

        try {
            UUID cashAccountId    = findAccountingAccountByCode(tenantId, POS_CASH_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountingAccountByCode(tenantId, POS_REVENUE_ACCOUNT_CODE);
            if (cashAccountId == null || revenueAccountId == null) {
                log.warn("[POS] Chart of Accounts missing account {} or {} for tenant={} — session={} sales not posted",
                        POS_CASH_ACCOUNT_CODE, POS_REVENUE_ACCOUNT_CODE, tenantId, session.getSessionNumber());
                return;
            }

            boolean hasVat = vatTotal.compareTo(BigDecimal.ZERO) > 0;
            UUID vatAccountId = null;
            if (hasVat) {
                vatAccountId = findAccountingAccountByCode(tenantId, POS_VAT_ACCOUNT_CODE);
                if (vatAccountId == null) {
                    log.warn("[POS] Chart of Accounts missing VAT Output ({}) for tenant={} — session={} sales not posted",
                            POS_VAT_ACCOUNT_CODE, tenantId, session.getSessionNumber());
                    return;
                }
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = new ArrayList<>();
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    cashAccountId, "POS sales — " + session.getSessionNumber(), totalSales, null));
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    revenueAccountId, "POS revenue — " + session.getSessionNumber(), null, subtotal));
            if (hasVat) {
                lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                        vatAccountId, "POS VAT output — " + session.getSessionNumber(), null, vatTotal));
            }

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "POS session closed: " + session.getSessionNumber(),
                    session.getSessionNumber(), "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("[POS] Posted sales journal for session={} tenant={} total={}",
                    session.getSessionNumber(), tenantId, totalSales);
        } catch (Exception e) {
            // Same principle as every other cross-module side-effect hookup
            // in this codebase: the session is already closed and saved by
            // the time this runs — a posting failure must never look like
            // it affected that (or block the cashier from closing out).
            log.error("[POS] Failed to post sales journal for session={} tenant={}: {}",
                    session.getSessionNumber(), tenantId, e.getMessage(), e);
        }
    }

    /**
     * AccountingFacade.getAccounts(TenantId) — added specifically for
     * this fix (see that class's own Javadoc) rather than replicating
     * AP's raw-JDBC account lookup, which would repeat the exact
     * facade-bypass pattern backlog item 9.3 flags as wrong elsewhere in
     * the same review.
     */
    private UUID findAccountingAccountByCode(TenantId tenantId, String code) {
        return accountingFacade.getAccounts(tenantId).stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(a -> a.id())
                .findFirst()
                .orElse(null);
    }

    private void evaluateCashVariance(TenantId tenantId, PosCashSession session, String closedByName) {
        if (session.getCashVariance().compareTo(BigDecimal.ZERO) == 0) return;

        PosSettings settings = posSettingsService.getOrCreate(tenantId);
        BigDecimal variance = session.getCashVariance().abs();
        BigDecimal expected = session.getOpeningFloat().add(session.getExpectedCash());

        BigDecimal pctFloor = expected.compareTo(BigDecimal.ZERO) > 0
                ? expected.multiply(settings.getCashVarianceTolerancePct()) : BigDecimal.ZERO;
        BigDecimal noAlertCeiling = settings.getCashVarianceToleranceAmount().max(pctFloor);
        if (variance.compareTo(noAlertCeiling) <= 0) return; // within tolerance — till-counting noise

        BigDecimal pctCritical = expected.compareTo(BigDecimal.ZERO) > 0
                ? expected.multiply(settings.getCashVarianceCriticalPct()) : BigDecimal.ZERO;
        BigDecimal criticalFloor = settings.getCashVarianceCriticalAmount().max(pctCritical);
        NotificationSeverity severity = variance.compareTo(criticalFloor) > 0
                ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING;

        notifyCashVariance(tenantId, session, closedByName, severity);
    }

    private void notifyCashVariance(TenantId tenantId, PosCashSession session, String closedByName,
                                    NotificationSeverity severity) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        BigDecimal variance = session.getCashVariance();
        boolean isShort = variance.compareTo(BigDecimal.ZERO) < 0;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.CASH_UP_VARIANCE)
                .severity(severity)
                .title((severity == NotificationSeverity.CRITICAL ? "Large cash-up variance: " : "Cash-up variance: ")
                        + session.getSessionNumber())
                .message(session.getSessionNumber() + " closed " + (isShort ? "short" : "over")
                        + " by R" + variance.abs().stripTrailingZeros().toPlainString()
                        + " (closed by " + closedByName + ").")
                .actionUrl("/pos/cash-sessions/" + session.getId())
                .sourceModule("pos")
                .sourceEntityId(session.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyLowStock(TenantId tenantId, PosStockItem stockItem) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        String itemName = getCatalogueItemName(tenantId, stockItem.getCatalogueItemId());
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.STOCK_LOW)
                .title("Low stock: " + itemName)
                .message(itemName + " is now at "
                        + stockItem.getQtyOnHand().stripTrailingZeros().toPlainString()
                        + " on hand (reorder level: "
                        + stockItem.getReorderLevel().stripTrailingZeros().toPlainString()
                        + "). Consider raising a purchase order.")
                .actionUrl("/pos/stock")
                .sourceModule("pos")
                .sourceEntityId(stockItem.getId().toString())
                .recipients(recipients)
                .build());
    }

    // FIX: backlog 10.2 — the old 3-arg notifyCashVariance(tenantId,
    // session, closedByName) overload (no severity, unconditional) was
    // removed entirely along with its only call site in
    // closeCashSession() — see that method's own comment. Only the
    // 4-arg, tolerance-aware version below remains, called exclusively
    // from evaluateCashVariance().

    // FIX: was calling isVatExempt() via reflection on CatalogueItem — confirmed
    // that method genuinely doesn't exist on the real entity (CatalogueItem.java
    // has no vatExempt field at all). Every call threw, every throw was silently
    // caught, and the catch block always returned the standard rate — every item
    // was charged 15% on every sale, unconditionally, regardless of any actual
    // exemption.
    //
    // CORRECTION from an earlier version of this fix: CatalogueItem doesn't need
    // a new vatExempt boolean added to it at all. It already has a fully-wired
    // per-item `vatRate` field (BigDecimal, defaults to 15.00, settable via the
    // existing PUT /catalogue/items/{id} endpoint) — an item that should be
    // VAT-exempt is simply one with vatRate = 0.00 already set on it. The
    // `vat_exempt` boolean column POS's own V55 migration added to
    // catalogue_items looks like an unfinished, redundant second mechanism that
    // was never actually wired into the entity — this uses the one that already
    // works instead of finishing the one that doesn't.
    private BigDecimal resolveVatRate(TenantId tenantId, UUID catalogueItemId) {
        if (catalogueItemId == null) return vatRateProvider.ratePercent();
        return catalogueFacade.findItemById(tenantId, catalogueItemId)
                .map(item -> item.vatRate() != null ? item.vatRate() : vatRateProvider.ratePercent())
                .orElse(vatRateProvider.ratePercent());
    }

    private String resolveItemName(TenantId tenantId, UUID catalogueItemId, String fallback) {
        if (catalogueItemId == null) return fallback != null ? fallback : "Custom Item";
        return catalogueFacade.findItemById(tenantId, catalogueItemId)
                .map(CatalogueItemSummary::name)
                .orElse(fallback != null ? fallback : "Unknown Item");
    }

    /**
     * CatalogueItem has no sku field — confirmed against the real entity,
     * not assumed. The reflection this replaced was calling a getSku()
     * method that doesn't exist, so this method has always returned null
     * for every call; the catch-and-return-null just hid that silently.
     * Returning null directly here isn't a regression — it's the same
     * observable behavior, now honest about why. If SKU support is
     * genuinely wanted for POS, that's a real feature (add the field to
     * CatalogueItem, expose it on CatalogueItemSummary, then this method
     * gets a real implementation) — not something to fake here.
     */
    private String resolveItemSku(TenantId tenantId, UUID catalogueItemId) {
        return null;
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
                                    String tenantName, String tenantAddress,
                                    String tenantPhone, String vatNumber) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
                .withZone(ZONE_SA);

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:monospace;max-width:300px;margin:0 auto'>");
        sb.append("<div style='text-align:center'><strong>").append(tenantName).append("</strong><br/>");
        // NEW: previously never rendered at all, regardless of whether real
        // data existed — conditionally shown now so this is ready the
        // moment tenantAddress/tenantPhone get wired to a real source.
        if (tenantAddress != null && !tenantAddress.isBlank()) sb.append(tenantAddress).append("<br/>");
        if (tenantPhone != null && !tenantPhone.isBlank()) sb.append("Tel: ").append(tenantPhone).append("<br/>");
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

        // FIX: was hardcoded "VAT (15%)" — no longer accurate now that VAT is
        // resolved per catalogue item (see resolveVatRate()); a cart mixing
        // standard-rated and VAT-exempt items has a blended effective rate
        // that isn't 15%, so labelling it as a fixed percentage is actively
        // misleading. Just "VAT" with the real computed amount instead.
        sb.append("<tr><td>VAT</td><td align='right'>R ").append(txn.getVatAmount()).append("</td></tr>");
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

    // Same jdbc-lookup pattern already proven correct elsewhere in this
    // codebase (Creative/Marketing services use the identical approach).
    /** name/phone/vatNumber/address (already formatted to a single display line, or null) */
    private record TenantProfile(String name, String phone, String vatNumber, String address) {}

    // Pulls the real company details set up for PDF invoice generation
    // (V9__tenant_company_details.sql) — the receipt now shows the same
    // business identity the Invoicing module's PDFs already use, rather
    // than duplicating a separate, incomplete lookup.
    private TenantProfile fetchTenantProfile(TenantId tenantId) {
        try {
            java.util.Map<String, Object> row = jdbc.queryForMap(
                    "SELECT name, phone, vat_number, address::text AS address FROM tenants WHERE id = ?",
                    tenantId.getValue());
            String name      = (String) row.get("name");
            String phone     = (String) row.get("phone");
            String vatNumber = (String) row.get("vat_number");
            String address   = formatAddress((String) row.get("address"));
            return new TenantProfile(name != null ? name : "Your Business", phone, vatNumber, address);
        } catch (Exception e) {
            return new TenantProfile("Your Business", null, null, null);
        }
    }

    // address is JSONB — {street, suburb, city, province, postalCode}, the
    // same shape CRM customer addresses already use — flattened into one
    // display-friendly line, skipping any parts that weren't filled in.
    private String formatAddress(String addressJson) {
        if (addressJson == null || addressJson.isBlank()) return null;
        try {
            java.util.Map<String, String> addr = objectMapper.readValue(
                    addressJson, new TypeReference<java.util.Map<String, String>>() {});
            return java.util.stream.Stream.of(
                            addr.get("street"), addr.get("suburb"), addr.get("city"),
                            addr.get("province"), addr.get("postalCode"))
                    .filter(s -> s != null && !s.isBlank())
                    .collect(java.util.stream.Collectors.joining(", "));
        } catch (Exception e) {
            return null;
        }
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

    // FIX (found while assembling this file for the Z-report fix, not part
    // of the original ask — HandyFlow BOS Discovery doc, Section 68): this
    // previously chained TWO .map() calls where the first already produced
    // the final BigDecimal (CatalogueItemSummary::defaultPrice), then the
    // second tried to reflectively call getDefaultPrice() ON THAT
    // BigDecimal — which has no such method, so it always threw and always
    // fell through to BigDecimal.ZERO. Every stock item's selling price on
    // this list has been showing R0.00 regardless of the real catalogue
    // price. This looks like leftover reflection code from before the
    // CatalogueFacade fix (see that class's own Javadoc) was applied — the
    // correct line was added on top of it, but the broken original was
    // never deleted. Removed the dead second .map() entirely; the first
    // one was already correct and sufficient on its own.
    private StockItemResponse mapStockItem(TenantId tenantId, PosStockItem s) {
        String itemName = resolveItemName(tenantId, s.getCatalogueItemId(), "Unknown");
        String sku      = resolveItemSku(tenantId, s.getCatalogueItemId());
        String barcode  = null; // resolve from catalogue if needed

        // Selling price comes from catalogue default_price
        BigDecimal sellingPrice = catalogueFacade.findItemById(tenantId, s.getCatalogueItemId())
                .map(CatalogueItemSummary::defaultPrice)
                .orElse(BigDecimal.ZERO);

        return new StockItemResponse(
                s.getId(), s.getCatalogueItemId(),
                itemName, sku, barcode,
                s.getQtyOnHand(), s.getQtyReserved(), s.getAvailableQty(),
                s.getReorderLevel(), s.getReorderQty(),
                s.getCostPrice(), sellingPrice,
                s.getLocation(), s.isLowStock(), s.getUpdatedAt());
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

    // NEW: was missing entirely from the file assembled in Section 69 —
    // called throughout (processSale, processRefund, getTransactions,
    // getTransaction, voidTransaction) but never once appeared in any
    // source retrieved that session. Added here from the real method body,
    // not reconstructed.
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

    // NEW: same situation as mapTransaction() above — called throughout
    // (getPurchaseOrders, createPurchaseOrder, receiveStock) but was
    // missing from the assembled file. Added from the real method body.
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

    // ── Inner record for stock deduction tracking ─────────────────────────────

    private record StockDeduction(PosStockItem stockItem, BigDecimal qty) {}
}