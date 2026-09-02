package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.AccountResponse;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseBillingInvoice;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipmentLine;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrder;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrderLine;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.domain.repository.WhseBillingInvoiceRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInboundShipmentLineRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInventoryRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseItemRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseOutboundOrderLineRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseOutboundOrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The centerpiece of this module — the only thing that posts to the real
 * chart of accounts (mirrors CollAgencyCommissionInvoice's role exactly,
 * see WhseBillingInvoice's own Javadoc).
 * <p>
 * <b>*** DELIBERATE SIMPLIFICATIONS — flagged, not silently guessed ***</b>
 * <ul>
 *   <li><b>Storage billing is a point-in-time snapshot, not true
 *   daily-accrual proration.</b> A real 3PL typically bills storage on
 *   either (a) a daily average of qtyOnHand across the period, or (b) a
 *   fixed snapshot taken on a specific day (e.g. "stock as at the last day
 *   of the month"). This implementation takes option (b) — qtyOnHand AT
 *   INVOICE-GENERATION TIME — multiplied by the resolved rate and prorated
 *   by (days in period / 30). This means a client who received a large
 *   shipment and shipped it all out again within the same period would be
 *   billed for whatever happened to be on hand at the moment
 *   generateInvoice() runs, not their true average holding. Building daily-
 *   accrual proration would need a new time-series/snapshot table this
 *   first pass doesn't have — this was a scope call, not something to
 *   silently pretend is accurate.</li>
 *   <li><b>Handling fees are computed by scanning shipments/orders
 *   touched since the last invoice's periodEnd</b>, not from a dedicated
 *   billable-events ledger. findReceivedForClientSince() filters by the
 *   shipment header's updatedAt, which is an approximation: if a
 *   multi-line shipment had one line received in the previous billing
 *   period and another line received in this one, the header's updatedAt
 *   only reflects the most recent touch, and the query could (rarely)
 *   include a line that was already substantially billed. This is a real
 *   edge case, not resolved here — flagged for the same reason the storage
 *   snapshot approach is flagged.</li>
 *   <li><b>VAT is left at zero</b> — same open question as
 *   CollAgencyCommissionInvoice's own vatAmount: this session should not
 *   guess a South African VAT treatment for a 3PL's storage/handling fees
 *   without confirmation. See module status doc.</li>
 *   <li><b>A missing rate does not block the whole invoice.</b> If no
 *   storage/receiving/pick/pack rate resolves for a given item or client
 *   (none set at item level, client level, or profile default), that
 *   fee component contributes R0 and a warning is logged — the invoice
 *   still generates for everything that DOES have a rate. This mirrors
 *   this codebase's established "skip and log, don't fail the whole
 *   operation over one missing config value" convention (see the GL
 *   account-lookup pattern in postBillingRevenueJournal below), rather
 *   than throwing and blocking billing for an entire client over one
 *   unconfigured item.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhseBillingService {

    private static final String AR_ACCOUNT_CODE = "1100";      // Accounts Receivable
    private static final String REVENUE_ACCOUNT_CODE = "4000"; // Revenue/Sales
    private static final String VAT_ACCOUNT_CODE = "2100";     // VAT Output
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30"); // flagged simplification, see class Javadoc

    private final WhseBillingInvoiceRepository invoiceRepository;
    private final WhseInventoryRepository inventoryRepository;
    private final WhseItemRepository itemRepository;
    private final WhseInboundShipmentLineRepository shipmentLineRepository;
    private final WhseOutboundOrderRepository outboundOrderRepository;
    private final WhseOutboundOrderLineRepository outboundOrderLineRepository;
    private final WhseClientService clientService;
    private final WhseProfileService profileService;
    private final WhseNumberGenerator numberGenerator;
    private final AccountingFacade accountingFacade;

    @Transactional
    public WhseBillingInvoice generateInvoice(TenantId tenantId, UUID clientId, LocalDate periodEnd) {
        WhseClient client = clientService.findActive(tenantId, clientId);
        WhseProfile profile = profileService.get(tenantId);

        LocalDate periodStart = invoiceRepository.findMostRecentForClient(tenantId.getValue(), clientId)
                .map(WhseBillingInvoice::getPeriodEnd)
                .orElseGet(() -> client.getOnboardedAt() != null ? client.getOnboardedAt() : periodEnd.minusMonths(1));
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("periodEnd (" + periodEnd
                    + ") must be after the last billed period end (" + periodStart + ")");
        }
        long daysInPeriod = ChronoUnit.DAYS.between(periodStart, periodEnd);

        BigDecimal storageFee = computeStorageFee(tenantId, clientId, client, profile, daysInPeriod);
        BigDecimal handlingFee = computeHandlingFee(tenantId, clientId, client, profile, periodStart);
        BigDecimal vatAmount = BigDecimal.ZERO; // VAT-on-invoice treatment is a real question this session should not guess at — see module status doc

        String invoiceNumber = numberGenerator.nextInvoiceNumber(tenantId, clientId);
        LocalDate invoiceDate = LocalDate.now();
        WhseBillingInvoice invoice = WhseBillingInvoice.create(tenantId.getValue(), clientId, invoiceNumber,
                periodStart, periodEnd, invoiceDate, invoiceDate.plusDays(30), storageFee, handlingFee, vatAmount);
        invoice = invoiceRepository.save(invoice);
        invoice.markSent();
        invoice = invoiceRepository.save(invoice);
        postBillingRevenueJournal(tenantId, invoice);

        log.info("[Warehousing] Billing invoice generated tenant={} client={} invoice={} storageFee={} handlingFee={} period={}..{}",
                tenantId.getValue(), clientId, invoiceNumber, storageFee, handlingFee, periodStart, periodEnd);
        return invoice;
    }

    @Transactional(readOnly = true)
    public Page<WhseBillingInvoice> listForClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        return invoiceRepository.findByClient(tenantId.getValue(), clientId, pageable);
    }

    @Transactional(readOnly = true)
    public WhseBillingInvoice get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional
    public WhseBillingInvoice recordPayment(TenantId tenantId, UUID id, BigDecimal amount) {
        WhseBillingInvoice invoice = findActive(tenantId, id);
        invoice.recordPayment(amount);
        return invoiceRepository.save(invoice);
    }

    private BigDecimal computeStorageFee(TenantId tenantId, UUID clientId, WhseClient client, WhseProfile profile,
                                          long daysInPeriod) {
        BigDecimal storageFee = BigDecimal.ZERO;
        List<WhseInventory> positions = inventoryRepository.findAllForClient(tenantId.getValue(), clientId);
        Map<UUID, WhseItem> itemsById = itemRepository.findAllActiveForClient(tenantId.getValue(), clientId).stream()
                .collect(Collectors.toMap(WhseItem::getId, i -> i));

        for (WhseInventory position : positions) {
            if (position.getQtyOnHand().signum() <= 0) {
                continue;
            }
            WhseItem item = itemsById.get(position.getItemId());
            BigDecimal rate = resolveStorageRate(item, client, profile);
            if (rate == null) {
                log.warn("[Warehousing] No storage rate resolvable (item/client/profile default all unset) for item={} client={} tenant={} — this position contributes R0 to storage fee, not guessed",
                        position.getItemId(), clientId, tenantId.getValue());
                continue;
            }
            BigDecimal monthlyFee = position.getQtyOnHand().multiply(rate);
            BigDecimal prorated = monthlyFee.multiply(BigDecimal.valueOf(daysInPeriod))
                    .divide(DAYS_PER_MONTH, 2, RoundingMode.HALF_UP);
            storageFee = storageFee.add(prorated);
        }
        return storageFee;
    }

    private BigDecimal computeHandlingFee(TenantId tenantId, UUID clientId, WhseClient client, WhseProfile profile,
                                           LocalDate periodStart) {
        BigDecimal handlingFee = BigDecimal.ZERO;
        Instant since = periodStart.atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal receivingFeePerUnit = resolveReceivingFeePerUnit(client, profile);
        if (receivingFeePerUnit != null) {
            List<WhseInboundShipmentLine> receivedLines =
                    shipmentLineRepository.findReceivedForClientSince(tenantId.getValue(), clientId, since);
            BigDecimal totalReceived = receivedLines.stream()
                    .map(WhseInboundShipmentLine::getReceivedQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            handlingFee = handlingFee.add(totalReceived.multiply(receivingFeePerUnit));
        } else {
            log.warn("[Warehousing] No receiving fee rate resolvable for client={} tenant={} — receiving handling fee not billed this period",
                    clientId, tenantId.getValue());
        }

        List<WhseOutboundOrder> shippedOrders =
                outboundOrderRepository.findShippedForClientSince(tenantId.getValue(), clientId, periodStart);
        BigDecimal pickFeePerUnit = resolvePickFeePerUnit(client, profile);
        if (pickFeePerUnit != null) {
            for (WhseOutboundOrder order : shippedOrders) {
                List<WhseOutboundOrderLine> lines = outboundOrderLineRepository.findByOrder(tenantId.getValue(), order.getId());
                BigDecimal orderQty = lines.stream().map(WhseOutboundOrderLine::getQtyOrdered)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                handlingFee = handlingFee.add(orderQty.multiply(pickFeePerUnit));
            }
        } else if (!shippedOrders.isEmpty()) {
            log.warn("[Warehousing] No pick fee rate resolvable for client={} tenant={} — pick handling fee not billed for {} shipped order(s) this period",
                    clientId, tenantId.getValue(), shippedOrders.size());
        }

        BigDecimal packFeePerOrder = resolvePackFeePerOrder(client, profile);
        if (packFeePerOrder != null) {
            handlingFee = handlingFee.add(packFeePerOrder.multiply(BigDecimal.valueOf(shippedOrders.size())));
        } else if (!shippedOrders.isEmpty()) {
            log.warn("[Warehousing] No pack fee rate resolvable for client={} tenant={} — pack handling fee not billed for {} shipped order(s) this period",
                    clientId, tenantId.getValue(), shippedOrders.size());
        }
        return handlingFee;
    }

    /** Resolution order: item override -&gt; client override -&gt; profile default. Returns null if none is set anywhere. */
    private BigDecimal resolveStorageRate(WhseItem item, WhseClient client, WhseProfile profile) {
        if (item != null && item.getStorageRatePerUnitPerMonth() != null) return item.getStorageRatePerUnitPerMonth();
        if (client.getStorageRatePerUnitPerMonth() != null) return client.getStorageRatePerUnitPerMonth();
        return profile != null ? profile.getDefaultStorageRatePerUnitPerMonth() : null;
    }

    private BigDecimal resolveReceivingFeePerUnit(WhseClient client, WhseProfile profile) {
        if (client.getReceivingFeePerUnit() != null) return client.getReceivingFeePerUnit();
        return profile != null ? profile.getDefaultReceivingFeePerUnit() : null;
    }

    private BigDecimal resolvePickFeePerUnit(WhseClient client, WhseProfile profile) {
        if (client.getPickFeePerUnit() != null) return client.getPickFeePerUnit();
        return profile != null ? profile.getDefaultPickFeePerUnit() : null;
    }

    private BigDecimal resolvePackFeePerOrder(WhseClient client, WhseProfile profile) {
        if (client.getPackFeePerOrder() != null) return client.getPackFeePerOrder();
        return profile != null ? profile.getDefaultPackFeePerOrder() : null;
    }

    private void postBillingRevenueJournal(TenantId tenantId, WhseBillingInvoice invoice) {
        try {
            UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(tenantId, REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                log.warn("[Warehousing] Chart of Accounts missing account {} or {} for tenant={} — billing invoice={} revenue not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId.getValue(), invoice.getId());
                return;
            }
            boolean hasVat = invoice.getVatAmount() != null && invoice.getVatAmount().compareTo(BigDecimal.ZERO) > 0;
            UUID vatAccountId = null;
            if (hasVat) {
                vatAccountId = findAccountByCode(tenantId, VAT_ACCOUNT_CODE);
                if (vatAccountId == null) {
                    log.warn("[Warehousing] Chart of Accounts missing VAT Output ({}) for tenant={} — billing invoice={} revenue not posted",
                            VAT_ACCOUNT_CODE, tenantId.getValue(), invoice.getId());
                    return;
                }
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = new java.util.ArrayList<>();
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    arAccountId, "Warehousing — " + invoice.getInvoiceNumber(), invoice.getTotal(), null));
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    revenueAccountId, "Storage + handling revenue — " + invoice.getInvoiceNumber(), null,
                    invoice.getSubtotal()));
            if (hasVat) {
                lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                        vatAccountId, "VAT output — " + invoice.getInvoiceNumber(), null, invoice.getVatAmount()));
            }

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Warehousing billing: " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber(), "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("[Warehousing] Posted billing revenue journal for invoice={} tenant={}",
                    invoice.getInvoiceNumber(), tenantId.getValue());
        } catch (Exception e) {
            log.error("[Warehousing] Failed to post billing revenue journal for invoice={} tenant={}: {}",
                    invoice.getId(), tenantId.getValue(), e.getMessage(), e);
        }
    }

    private UUID findAccountByCode(TenantId tenantId, String code) {
        List<AccountResponse> accounts = accountingFacade.getAccounts(tenantId);
        return accounts.stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(AccountResponse::id)
                .findFirst()
                .orElse(null);
    }

    private WhseBillingInvoice findActive(TenantId tenantId, UUID id) {
        return invoiceRepository.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("WhseBillingInvoice", id.toString()));
    }
}
