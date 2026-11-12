package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.SupplierInvoiceDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import za.co.handyflow.platform.ap.application.ApFacade;
import za.co.handyflow.platform.billing.FeatureGuard;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelSupplierInvoiceServiceTest {

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID(), SUPPLIER = UUID.randomUUID();

    @Mock FuelSupplierInvoiceRepository invoices;
    @Mock FuelSupplierInvoiceReceiptRepository links;
    @Mock FuelReceiptRepository receipts;
    @Mock FuelPurchaseOrderRepository orders;
    @Mock FuelSupplierRepository suppliers;
    @Mock ApFacade ap;
    @Mock FeatureGuard featureGuard;
    @InjectMocks FuelSupplierInvoiceService service;

    FuelSupplier supplier;
    FuelPurchaseOrder po;
    FuelReceipt r1, r2;
    final List<FuelSupplierInvoice> savedInvoices = new ArrayList<>();
    final List<FuelSupplierInvoiceReceipt> savedLinks = new ArrayList<>();

    static BigDecimal d(String s) { return new BigDecimal(s); }

    private FuelReceipt receipt(String litres, String price) {
        return FuelReceipt.create(TENANT, UUID.randomUUID(), SUPPLIER, d(litres), d(price), Instant.now(), "DN-1", null, d("0"), d(litres));
    }

    @BeforeEach
    void setUp() {
        supplier = mock(FuelSupplier.class);
        when(supplier.getId()).thenReturn(SUPPLIER);
        when(supplier.getName()).thenReturn("Acme Fuels");
        when(suppliers.findActiveById(TENANT, SUPPLIER)).thenReturn(Optional.of(supplier));
        po = FuelPurchaseOrder.create(TENANT, "PO-2026-00001", SUPPLIER, "DIESEL", d("10000"), d("20.00"), null, null, USER);
        r1 = receipt("6000", "20.00");
        r2 = receipt("4000", "20.00");
        when(orders.findActiveById(TENANT, po.getId())).thenReturn(Optional.of(po));
        when(receipts.findActiveByIds(eq(TENANT), any())).thenAnswer(i -> {
            Collection<UUID> ids = i.getArgument(1);
            return List.of(r1, r2).stream().filter(r -> ids.contains(r.getId())).toList();
        });
        when(links.findAlreadyLinked(eq(TENANT), any())).thenReturn(List.of());
        when(invoices.existsLive(eq(TENANT), eq(SUPPLIER), any())).thenReturn(false);
        when(invoices.save(any())).thenAnswer(i -> { FuelSupplierInvoice v = i.getArgument(0); savedInvoices.add(v); return v; });
        when(links.save(any())).thenAnswer(i -> { savedLinks.add(i.getArgument(0)); return i.getArgument(0); });
        when(links.findByInvoices(eq(TENANT), any())).thenAnswer(i -> savedLinks);
    }

    private CaptureRequest req(String number, String litres, String price, UUID poId, List<UUID> ids) {
        return new CaptureRequest(SUPPLIER, poId, number, LocalDate.of(2026, 11, 10), null, d(litres), d(price), null, null, null, ids);
    }

    // ── the matcher ──────────────────────────────────────────────────────────

    @Test @DisplayName("litres and price that agree with the order and the receipts match")
    void matches() {
        var m = SupplierInvoiceMatcher.match(d("10000"), d("20.00"), List.of(r1, r2), po);
        assertEquals("MATCHED", m.status());
        assertTrue(m.issues().isEmpty());
        assertEquals(0, d("0").compareTo(m.litresVariance()));
        assertEquals(0, d("200000").compareTo(m.goodsValue()));
    }

    @Test @DisplayName("0.5% on litres is tolerated, more is a variance that names the difference")
    void litresTolerance() {
        assertEquals("MATCHED", SupplierInvoiceMatcher.match(d("10040"), d("20.00"), List.of(r1, r2), po).status());
        var m = SupplierInvoiceMatcher.match(d("10200"), d("20.00"), List.of(r1, r2), po);
        assertEquals("VARIANCE", m.status());
        assertTrue(m.issues().get(0).contains("+200"));
    }

    @Test @DisplayName("a price above the PO price or the booked receipt price is a variance")
    void priceVariance() {
        var m = SupplierInvoiceMatcher.match(d("10000"), d("20.10"), List.of(r1, r2), po);
        assertEquals("VARIANCE", m.status());
        assertEquals(2, m.issues().size()); // against receiving and against the PO
        assertEquals(0, d("0.10").compareTo(m.priceVsPo()));
        assertEquals("MATCHED", SupplierInvoiceMatcher.match(d("10000"), d("20.004"), List.of(r1, r2), po).status());
    }

    @Test @DisplayName("no linked receipts is UNMATCHED; a reversed receipt does not count as received")
    void unmatchedAndReversed() {
        assertEquals("UNMATCHED", SupplierInvoiceMatcher.match(d("10000"), d("20"), List.of(), po).status());
        r2.reverse(USER, "wrong tank");
        var m = SupplierInvoiceMatcher.match(d("10000"), d("20"), List.of(r1, r2), po);
        assertEquals("VARIANCE", m.status());
        assertEquals(0, d("6000").compareTo(m.litresReceived()));
        assertTrue(m.issues().stream().anyMatch(s -> s.contains("reversed")));
    }

    @Test @DisplayName("with no PO only receiving is checked")
    void noPo() {
        assertEquals("MATCHED", SupplierInvoiceMatcher.match(d("10000"), d("20.00"), List.of(r1, r2), null).status());
    }

    // ── capture ──────────────────────────────────────────────────────────────

    @Test @DisplayName("capture totals the invoice (litres x price + charges + VAT), links the receipts and records the match")
    void captureMatched() {
        CaptureRequest rq = new CaptureRequest(SUPPLIER, po.getId(), " INV-100 ", LocalDate.of(2026, 11, 10), null, d("10000"), d("20.00"), d("500.00"), d("30075.00"), null, List.of(r1.getId(), r2.getId()));
        InvoiceResponse r = service.capture(TENANT, rq, USER);
        assertEquals("INV-100", r.invoiceNumber());
        assertEquals(0, d("230575.00").compareTo(r.totalAmount()));
        assertEquals("MATCHED", r.matchStatus());
        assertEquals("CAPTURED", r.status());
        assertEquals(2, savedLinks.size());
        assertEquals("Acme Fuels", r.supplierName());
    }

    @Test @DisplayName("a duplicate invoice number from the same supplier is refused")
    void duplicateNumber() {
        when(invoices.existsLive(eq(TENANT), eq(SUPPLIER), any())).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.capture(TENANT, req("INV-1", "10000", "20", null, List.of()), USER));
        assertTrue(savedInvoices.isEmpty());
    }

    @Test @DisplayName("receipts must be from the supplier, not reversed, not already invoiced, and on the same PO")
    void receiptRules() {
        FuelReceipt other = FuelReceipt.create(TENANT, UUID.randomUUID(), UUID.randomUUID(), d("100"), d("20"), Instant.now(), null, null, d("0"), d("100"));
        r1 = other;
        assertThrows(IllegalArgumentException.class, () -> service.capture(TENANT, req("A", "100", "20", null, List.of(other.getId())), USER));
        r1 = receipt("6000", "20.00");
        r1.reverse(USER, "x");
        assertThrows(IllegalArgumentException.class, () -> service.capture(TENANT, req("B", "6000", "20", null, List.of(r1.getId())), USER));
        r1 = receipt("6000", "20.00");
        when(links.findAlreadyLinked(eq(TENANT), any())).thenReturn(List.of(r1.getId()));
        assertThrows(IllegalStateException.class, () -> service.capture(TENANT, req("C", "6000", "20", null, List.of(r1.getId())), USER));
        when(links.findAlreadyLinked(eq(TENANT), any())).thenReturn(List.of());
        r1.assignPurchaseOrder(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> service.capture(TENANT, req("D", "6000", "20", po.getId(), List.of(r1.getId())), USER));
        assertTrue(savedInvoices.isEmpty());
    }

    @Test @DisplayName("a PO for another supplier, or a PO that does not exist, is refused")
    void poRules() {
        FuelPurchaseOrder foreign = FuelPurchaseOrder.create(TENANT, "PO-2", UUID.randomUUID(), "DIESEL", d("1"), d("1"), null, null, USER);
        when(orders.findActiveById(TENANT, foreign.getId())).thenReturn(Optional.of(foreign));
        assertThrows(IllegalArgumentException.class, () -> service.capture(TENANT, req("A", "1", "1", foreign.getId(), List.of()), USER));
        assertThrows(ResourceNotFoundException.class, () -> service.capture(TENANT, req("B", "1", "1", UUID.randomUUID(), List.of()), USER));
    }

    // ── decisions ────────────────────────────────────────────────────────────

    private FuelSupplierInvoice capturedWith(String litres, String price) {
        service.capture(TENANT, req("INV-" + litres + price, litres, price, po.getId(), List.of(r1.getId(), r2.getId())), USER);
        FuelSupplierInvoice inv = savedInvoices.get(savedInvoices.size() - 1);
        when(invoices.findByTenantAndId(TENANT, inv.getId())).thenReturn(Optional.of(inv));
        return inv;
    }

    @Test @DisplayName("a matching invoice is approved without a reason")
    void approveMatched() {
        FuelSupplierInvoice inv = capturedWith("10000", "20.00");
        InvoiceResponse r = service.approve(TENANT, inv.getId(), null, USER);
        assertEquals("APPROVED", r.status());
        assertEquals("MATCHED", r.matchStatus());
    }

    // ---- B08: hand-off to Accounts Payable ----

    @Test @DisplayName("B08: approving with AP on creates one AP bill from the invoice (ex-VAT amount, due date, supplier name) and records it")
    void approveHandsToAp() {
        when(featureGuard.hasModule("ap")).thenReturn(true);
        UUID bill = UUID.randomUUID();
        when(ap.createBillFromFuelInvoice(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(bill);
        FuelSupplierInvoice inv = capturedWith("10000", "20.00");
        InvoiceResponse r = service.approve(TENANT, inv.getId(), null, USER);
        assertEquals(bill, r.apBillId());
        verify(ap).createBillFromFuelInvoice(eq(TENANT), eq("Acme Fuels"), eq(inv.getId().toString()), eq("FUEL-" + inv.getInvoiceNumber()),
                eq(inv.getInvoiceDate()), any(), eq(inv.getTotalAmount().subtract(inv.getVatAmount())), eq(inv.getVatAmount()), eq(USER));
    }

    @Test @DisplayName("B08: with AP off, approving works as before and nothing is sent")
    void approveWithoutAp() {
        when(featureGuard.hasModule("ap")).thenReturn(false);
        FuelSupplierInvoice inv = capturedWith("10000", "20.00");
        InvoiceResponse r = service.approve(TENANT, inv.getId(), null, USER);
        assertEquals("APPROVED", r.status());
        assertNull(r.apBillId());
        verifyNoInteractions(ap);
    }

    @Test @DisplayName("B08: if AP refuses, the approval fails rather than leaving an approved invoice with no bill")
    void apFailureStopsApproval() {
        when(featureGuard.hasModule("ap")).thenReturn(true);
        when(ap.createBillFromFuelInvoice(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("AP down"));
        FuelSupplierInvoice inv = capturedWith("10000", "20.00");
        assertThrows(IllegalStateException.class, () -> service.approve(TENANT, inv.getId(), null, USER));
        verify(invoices, never()).save(argThat(x -> x == inv && x.isHandedToAp()));
    }

    @Test @DisplayName("B08: sending an approved invoice later hands it over once, and a repeat does not call AP again")
    void sendToApLaterIsOnce() {
        FuelSupplierInvoice inv = capturedWith("10000", "20.00");
        service.approve(TENANT, inv.getId(), null, USER);
        when(featureGuard.hasModule("ap")).thenReturn(true);
        UUID bill = UUID.randomUUID();
        when(ap.createBillFromFuelInvoice(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(bill);
        assertEquals(bill, service.sendToAp(TENANT, inv.getId(), USER).apBillId());
        service.sendToAp(TENANT, inv.getId(), USER);
        verify(ap, times(1)).createBillFromFuelInvoice(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("B08: only an approved invoice can be sent to AP")
    void onlyApprovedGoes() {
        FuelSupplierInvoice inv = capturedWith("10000", "20.00");
        assertThrows(IllegalStateException.class, () -> service.sendToAp(TENANT, inv.getId(), USER));
        verifyNoInteractions(ap);
    }

    @Test @DisplayName("a mismatch needs a reason to approve, and the reason is kept")
    void approveVarianceNeedsReason() {
        FuelSupplierInvoice inv = capturedWith("10500", "20.00");
        assertThrows(IllegalArgumentException.class, () -> service.approve(TENANT, inv.getId(), new DecisionRequest("ok"), USER));
        assertEquals("CAPTURED", inv.getStatus());
        InvoiceResponse r = service.approve(TENANT, inv.getId(), new DecisionRequest("Supplier confirmed extra 500 L, credit note to follow"), USER);
        assertEquals("APPROVED", r.status());
        assertEquals("VARIANCE", r.matchStatus());
        assertTrue(r.decisionReason().contains("credit note"));
    }

    @Test @DisplayName("a receipt reversed after capture turns a matched invoice into a variance at approval")
    void matchIsRecomputedAtApproval() {
        FuelSupplierInvoice inv = capturedWith("10000", "20.00");
        assertEquals("MATCHED", inv.getMatchStatus());
        r2.reverse(USER, "wrong tank");
        assertThrows(IllegalArgumentException.class, () -> service.approve(TENANT, inv.getId(), null, USER));
    }

    @Test @DisplayName("rejecting frees the receipts, needs a reason, and an approved invoice cannot be rejected or approved again")
    void rejectAndFinality() {
        FuelSupplierInvoice inv = capturedWith("10000", "20.00");
        assertThrows(IllegalArgumentException.class, () -> service.reject(TENANT, inv.getId(), new DecisionRequest("no"), USER));
        InvoiceResponse r = service.reject(TENANT, inv.getId(), new DecisionRequest("Wrong supplier invoice"), USER);
        assertEquals("REJECTED", r.status());
        verify(links).deleteAll(any());
        assertThrows(IllegalStateException.class, () -> service.approve(TENANT, inv.getId(), new DecisionRequest("try anyway"), USER));

        FuelSupplierInvoice inv2 = capturedWith("10000", "20.00");
        service.approve(TENANT, inv2.getId(), null, USER);
        assertThrows(IllegalStateException.class, () -> service.approve(TENANT, inv2.getId(), null, USER));
        assertThrows(IllegalStateException.class, () -> service.reject(TENANT, inv2.getId(), new DecisionRequest("changed my mind"), USER));
    }

    @Test @DisplayName("another tenant's invoice is not found")
    void tenantIsolation() {
        TenantId other = TenantId.of(UUID.randomUUID());
        when(invoices.findByTenantAndId(eq(other), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(other, UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> service.approve(other, UUID.randomUUID(), null, USER));
    }
}
