package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.*;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.PurchaseOrderMatchResponse;
import za.co.handyflow.platform.facilities.dto.VendorInvoiceRequest;
import za.co.handyflow.platform.facilities.dto.VendorInvoiceResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Supplier invoices: recording, duplicates, the three-way match, approval rules and approval limits. */
class FacilityVendorInvoiceServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID clerk = UUID.randomUUID();
    private final UUID boss = UUID.randomUUID();
    private final FacilityVendor vendor = FacilityVendor.create(tenant, "ACME Supplies", "PARTS", null, null, null, null);
    private final Map<UUID, FacilityPurchaseOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, FacilityVendorInvoice> invoices = new LinkedHashMap<>();
    private final List<FacilityGoodsReceipt> receipts = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private final Set<String> authorities = new HashSet<>();

    @SuppressWarnings("unchecked")
    private <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if ("toString".equals(m.getName())) return type.getSimpleName();
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();
    private static BigDecimal n(String s) { return new BigDecimal(s); }

    private final FacilityVendorInvoiceRepository invoiceRepo = fake(FacilityVendorInvoiceRepository.class, (nme, a) -> switch (nme) {
        case "save" -> { FacilityVendorInvoice i = (FacilityVendorInvoice) a[0]; invoices.put(i.getId(), i); yield i; }
        case "findByIdForTenant" -> Optional.ofNullable(invoices.get(a[1]));
        case "findForPurchaseOrder" -> invoices.values().stream().filter(i -> i.getPurchaseOrderId().equals(a[1])).toList();
        case "existsForVendor" -> invoices.values().stream().anyMatch(i -> i.getVendorId().equals(a[1]) && i.getInvoiceNumber().equalsIgnoreCase((String) a[2]));
        default -> NOPE;
    });
    private final FacilityPurchaseOrderRepository orderRepo = fake(FacilityPurchaseOrderRepository.class, (nme, a) ->
            "findForTenant".equals(nme) ? Optional.ofNullable(orders.get(a[1])) : NOPE);
    private final FacilityGoodsReceiptRepository receiptRepo = fake(FacilityGoodsReceiptRepository.class, (nme, a) ->
            "findForPurchaseOrder".equals(nme) ? receipts.stream().filter(r -> r.getPurchaseOrderId().equals(a[1])).toList() : NOPE);
    private final FacilityVendorRepository vendorRepo = fake(FacilityVendorRepository.class, (nme, a) ->
            "findActiveById".equals(nme) ? Optional.of(vendor) : NOPE);
    private final FacilityAccessPolicy policy = new FacilityAccessPolicy(null, null, null) {
        @Override public boolean hasAuthority(String a) { return authorities.contains(a); }
    };
    private final FacilityAuditService auditService = new FacilityAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FacilityVendorInvoiceService service = new FacilityVendorInvoiceService(invoiceRepo, orderRepo, receiptRepo, vendorRepo, policy, auditService);

    /** An approved order of 10 x R100 = R1000, with {@code received} of the 10 delivered at the ordered price. */
    private FacilityPurchaseOrder order(int received) {
        FacilityPurchaseOrder po = FacilityPurchaseOrder.create(tenant, "FPO-1", vendor.getId(), null, null, clerk,
                List.of(new FacilityPurchaseOrder.LineSpec(UUID.randomUUID(), "Bolts", n("10"), n("100.00"))));
        po.approve(boss);
        if (received > 0) {
            FacilityPurchaseOrderLine line = po.getLines().get(0);
            po.receiveLine(line.getId(), n(String.valueOf(received)));
            receipts.add(FacilityGoodsReceipt.create(tenant, "FGR-1", po.getId(), vendor.getId(), null, LocalDate.now(), clerk,
                    List.of(new FacilityGoodsReceipt.LineSpec(line.getId(), line.getItemId(), line.getDescription(), n(String.valueOf(received)), n("100.00")))));
        }
        orders.put(po.getId(), po);
        return po;
    }

    private VendorInvoiceResponse record(FacilityPurchaseOrder po, String number, String subtotal) {
        TenantContext.setUserId(clerk.toString());
        return service.record(tenant, new VendorInvoiceRequest(po.getId(), number, LocalDate.now(), n(subtotal), n("0")));
    }

    @Test
    void recordingTakesTheVendorFromTheOrderAndIsAudited() {
        FacilityPurchaseOrder po = order(10);
        VendorInvoiceResponse r = record(po, "INV-1", "1000.00");
        assertEquals("RECORDED", r.status());
        assertEquals(vendor.getId(), r.vendorId());
        assertEquals("FPO-1", r.poNumber());
        assertEquals("ACME Supplies", r.vendorName());
        assertEquals(List.of("INVOICE_RECORDED"), audit);
    }

    @Test
    void aDraftOrCancelledOrderCannotBeInvoiced() {
        FacilityPurchaseOrder draft = FacilityPurchaseOrder.create(tenant, "FPO-2", vendor.getId(), null, null, clerk,
                List.of(new FacilityPurchaseOrder.LineSpec(UUID.randomUUID(), "Bolts", n("1"), n("1"))));
        orders.put(draft.getId(), draft);
        assertEquals("PO_NOT_INVOICEABLE", assertThrows(HandyFlowException.class, () -> record(draft, "INV-9", "10")).getErrorCode());
    }

    @Test
    void theSameInvoiceNumberCannotBeEnteredTwiceForAVendorRegardlessOfCase() {
        FacilityPurchaseOrder po = order(10);
        record(po, "Inv-1", "500.00");
        assertEquals("INVOICE_EXISTS", assertThrows(HandyFlowException.class, () -> record(po, "INV-1", "500.00")).getErrorCode());
    }

    @Test
    void anUnknownOrderIsNotFound() {
        TenantContext.setUserId(clerk.toString());
        assertThrows(ResourceNotFoundException.class, () -> service.record(tenant,
                new VendorInvoiceRequest(UUID.randomUUID(), "X", LocalDate.now(), n("1"), null)));
    }

    // ── match ────────────────────────────────────────────────────────────────

    @Test
    void theMatchComparesOrderedReceivedAndInvoiced() {
        FacilityPurchaseOrder po = order(6);   // R600 received of R1000 ordered
        record(po, "INV-1", "600.00");
        PurchaseOrderMatchResponse m = service.match(tenant, po.getId());
        assertEquals(0, n("1000.00").compareTo(m.orderedValue()));
        assertEquals(0, n("600.00").compareTo(m.receivedValue()));
        assertEquals(0, n("600.00").compareTo(m.invoicedValue()));
        assertEquals("MATCHED", m.status());
        assertEquals(1, m.invoices().size());
    }

    @Test
    void disputedInvoicesDoNotCountTowardTheInvoicedTotal() {
        FacilityPurchaseOrder po = order(10);
        VendorInvoiceResponse bad = record(po, "INV-1", "2500.00");
        TenantContext.setUserId(boss.toString());
        service.dispute(tenant, bad.id(), "Wrong price");
        assertEquals("NOT_INVOICED", service.match(tenant, po.getId()).status());
        assertEquals(0, BigDecimal.ZERO.compareTo(service.match(tenant, po.getId()).invoicedValue()));
    }

    // ── approval ─────────────────────────────────────────────────────────────

    @Test
    void aMatchedInvoiceIsApprovedByAnotherPersonWithoutANote() {
        FacilityPurchaseOrder po = order(10);
        VendorInvoiceResponse r = record(po, "INV-1", "1000.00");
        TenantContext.setUserId(boss.toString());
        VendorInvoiceResponse a = service.approve(tenant, r.id(), null);
        assertEquals("APPROVED", a.status());
        assertEquals(boss, a.decidedByUserId());
        assertEquals(List.of("INVOICE_RECORDED", "INVOICE_APPROVED"), audit);
    }

    @Test
    void youCannotApproveYourOwnInvoiceUnlessAdmin() {
        FacilityPurchaseOrder po = order(10);
        VendorInvoiceResponse r = record(po, "INV-1", "1000.00");
        TenantContext.setUserId(clerk.toString());
        assertEquals("INVOICE_SELF_APPROVAL", assertThrows(HandyFlowException.class, () -> service.approve(tenant, r.id(), null)).getErrorCode());
        authorities.add("FACILITIES_ADMIN");
        assertEquals("APPROVED", service.approve(tenant, r.id(), null).status());
        assertTrue(audit.contains("INVOICE_SELF_APPROVED"));
    }

    @Test
    void billingMoreThanWasReceivedNeedsAReasonAndIsAuditedAsAVariance() {
        FacilityPurchaseOrder po = order(5);   // R500 received
        VendorInvoiceResponse r = record(po, "INV-1", "1000.00");
        TenantContext.setUserId(boss.toString());
        assertEquals("INVOICE_VARIANCE_NOTE_REQUIRED", assertThrows(HandyFlowException.class, () -> service.approve(tenant, r.id(), " ")).getErrorCode());
        assertEquals("RECORDED", invoices.get(r.id()).getStatus());
        VendorInvoiceResponse a = service.approve(tenant, r.id(), "Rest delivered tomorrow, GRN pending");
        assertEquals("APPROVED", a.status());
        assertEquals("Rest delivered tomorrow, GRN pending", a.decisionNote());
        assertTrue(audit.contains("INVOICE_APPROVED_WITH_VARIANCE"));
    }

    @Test
    void billingLessThanReceivedNeedsNoReason() {
        FacilityPurchaseOrder po = order(10);
        VendorInvoiceResponse r = record(po, "INV-1", "400.00");
        TenantContext.setUserId(boss.toString());
        assertEquals("APPROVED", service.approve(tenant, r.id(), null).status());
    }

    @Test
    void aSecondInvoiceTakingTheOrderOverReceivedNeedsAReason() {
        FacilityPurchaseOrder po = order(10);
        VendorInvoiceResponse first = record(po, "INV-1", "900.00");
        VendorInvoiceResponse second = record(po, "INV-2", "300.00");   // 1200 vs 1000 received
        TenantContext.setUserId(boss.toString());
        service.approve(tenant, first.id(), null);                       // 900 alone is fine... but 1200 counted overall
        assertEquals("INVOICE_VARIANCE_NOTE_REQUIRED", assertThrows(HandyFlowException.class, () -> service.approve(tenant, second.id(), null)).getErrorCode());
    }

    @Test
    void anApprovedInvoiceCannotBeApprovedAgainOrDisputed() {
        FacilityPurchaseOrder po = order(10);
        VendorInvoiceResponse r = record(po, "INV-1", "1000.00");
        TenantContext.setUserId(boss.toString());
        service.approve(tenant, r.id(), null);
        assertEquals("INVOICE_DECIDED", assertThrows(HandyFlowException.class, () -> service.approve(tenant, r.id(), null)).getErrorCode());
        assertEquals("INVOICE_DECIDED", assertThrows(HandyFlowException.class, () -> service.dispute(tenant, r.id(), "late")).getErrorCode());
    }

    @Test
    void disputingNeedsAReasonAndADisputedInvoiceCanLaterBeApproved() {
        FacilityPurchaseOrder po = order(10);
        VendorInvoiceResponse r = record(po, "INV-1", "1000.00");
        TenantContext.setUserId(boss.toString());
        assertThrows(IllegalArgumentException.class, () -> service.dispute(tenant, r.id(), " "));
        assertEquals("DISPUTED", service.dispute(tenant, r.id(), "Qty short").status());
        assertTrue(audit.contains("INVOICE_DISPUTED"));
        assertEquals("APPROVED", service.approve(tenant, r.id(), null).status());
    }
}
