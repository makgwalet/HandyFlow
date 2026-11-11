package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.AdjustStockRequest;
import za.co.handyflow.platform.fuel.dto.CreateSupplierRequest;
import za.co.handyflow.platform.fuel.dto.FuelWholesaleDtos.AdjustOutcome;
import za.co.handyflow.platform.fuel.dto.MovementResponse;
import za.co.handyflow.platform.fuel.dto.PurchaseOrderResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelWholesaleControlsServiceTest {

    static final UUID TU = UUID.randomUUID();
    static final TenantId T = TenantId.of(TU);
    static final UUID ALICE = UUID.randomUUID(), BOB = UUID.randomUUID(), TANK = UUID.randomUUID();

    @Mock FuelPurchaseOrderRepository orders;
    @Mock FuelReceiptRepository receipts;
    @Mock FuelSupplierRepository suppliers;
    @Mock PurchaseOrderNumberGenerator numbers;
    @Mock FuelSettingsService settings;
    @Mock ApprovalFacade approvals;
    @Mock FuelStockService stock;
    @Mock FuelStockAdjustmentRequestRepository requests;
    @Mock FuelTankRepository tanks;

    FuelPurchasingService purchasing;
    FuelSupplierApprovalService supplierApproval;
    FuelStockAdjustmentApprovalService adjustments;
    FuelSettings cfg;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static MovementResponse movement() { return new MovementResponse(UUID.randomUUID(), TANK, "ADJUSTMENT", bd("-800"), bd("0"), "ADJUSTMENT", null, "reason", ALICE, java.time.Instant.now()); }

    @BeforeEach
    void setUp() {
        purchasing = new FuelPurchasingService(orders, receipts, suppliers, numbers, settings, approvals);
        supplierApproval = new FuelSupplierApprovalService(suppliers, settings);
        adjustments = new FuelStockAdjustmentApprovalService(stock, settings, requests, tanks);
        cfg = FuelSettings.forTenant(T);
        when(settings.current(any(TenantId.class))).thenReturn(cfg);
        when(requests.save(any())).thenAnswer(i -> i.getArgument(0));
        when(suppliers.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orders.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ApprovalRequestResponse outcome(String status) {
        return new ApprovalRequestResponse(UUID.randomUUID(), "fuel", "PURCHASE_ORDER", UUID.randomUUID(), status, null, ALICE, null, null, null, null);
    }

    private FuelPurchaseOrder draft(UUID supplierId) {
        FuelPurchaseOrder p = FuelPurchaseOrder.create(T, "PO-1", supplierId, "DIESEL_50", bd("1000"), bd("20"), null, null, ALICE);
        when(orders.findActiveById(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(p));
        return p;
    }

    private FuelSupplier approvedSupplier() {
        FuelSupplier s = FuelSupplier.create(T, "Acme", null, null, null, null);
        when(suppliers.findActiveById(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(s));
        return s;
    }

    // ── suppliers ────────────────────────────────────────────────────────────
    @Test @DisplayName("a new supplier is approved straight away when the setting is off")
    void createdApprovedByDefault() {
        assertEquals("APPROVED", supplierApproval.create(T, new CreateSupplierRequest("Acme", null, null, null, null), ALICE).approvalStatus());
    }

    @Test @DisplayName("with the setting on, a new supplier waits and its creator cannot approve it")
    void createdPendingWhenRequired() {
        cfg.updateControls(true, null);
        var created = supplierApproval.create(T, new CreateSupplierRequest("Acme", null, null, null, null), ALICE);
        assertEquals("PENDING_APPROVAL", created.approvalStatus());
        FuelSupplier s = FuelSupplier.create(T, "Acme", null, null, null, null); s.requireApproval(ALICE);
        when(suppliers.findActiveById(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(s));
        assertThrows(IllegalStateException.class, () -> supplierApproval.approve(T, s.getId(), ALICE));
        assertEquals("APPROVED", supplierApproval.approve(T, s.getId(), BOB).approvalStatus());
    }

    @Test @DisplayName("approving a supplier of another company finds nothing")
    void approveOtherTenant() {
        when(suppliers.findActiveById(any(TenantId.class), any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> supplierApproval.approve(T, UUID.randomUUID(), BOB));
    }

    // ── purchase orders ──────────────────────────────────────────────────────
    @Test @DisplayName("with no approval rule the order is placed immediately")
    void placeAutoApproved() {
        FuelSupplier s = approvedSupplier(); FuelPurchaseOrder p = draft(s.getId());
        when(approvals.submit(any(TenantId.class), eq("fuel"), eq("PURCHASE_ORDER"), any(), any(), anyMap())).thenReturn(outcome("APPROVED"));
        PurchaseOrderResponse r = purchasing.place(T, p.getId(), ALICE);
        assertEquals("ORDERED", r.status());
    }

    @Test @DisplayName("with a matching rule the order waits for approval and sends the order value")
    void placeWaits() {
        FuelSupplier s = approvedSupplier(); FuelPurchaseOrder p = draft(s.getId());
        when(approvals.submit(any(TenantId.class), any(), any(), any(), any(), anyMap())).thenReturn(outcome("SUBMITTED"));
        assertEquals("PENDING_APPROVAL", purchasing.place(T, p.getId(), ALICE).status());
        verify(approvals).submit(eq(T), eq("fuel"), eq("PURCHASE_ORDER"), eq(p.getId()), eq(ALICE), argThatMap(20000.0));
    }

    private static Map<String, Object> argThatMap(double total) {
        return org.mockito.ArgumentMatchers.argThat(m -> m != null && Double.valueOf(total).equals(m.get("totalAmount")));
    }

    @Test @DisplayName("a failure to reach the Approvals engine does not place the order")
    void placeFailsClosed() {
        FuelSupplier s = approvedSupplier(); FuelPurchaseOrder p = draft(s.getId());
        when(approvals.submit(any(TenantId.class), any(), any(), any(), any(), anyMap())).thenThrow(new RuntimeException("down"));
        assertThrows(RuntimeException.class, () -> purchasing.place(T, p.getId(), ALICE));
        assertEquals("DRAFT", p.getStatus());
    }

    @Test @DisplayName("an order cannot be placed with a suspended supplier")
    void placeSuspendedSupplier() {
        FuelSupplier s = approvedSupplier(); s.suspend(BOB, "Licence expired");
        FuelPurchaseOrder p = draft(s.getId());
        assertThrows(IllegalStateException.class, () -> purchasing.place(T, p.getId(), ALICE));
        verifyNoInteractions(approvals);
    }

    @Test @DisplayName("an order cannot be raised with a supplier still waiting for approval")
    void createPendingSupplier() {
        FuelSupplier s = FuelSupplier.create(T, "Acme", null, null, null, null); s.requireApproval(ALICE);
        when(suppliers.findActiveById(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(s));
        var req = new za.co.handyflow.platform.fuel.dto.PurchaseOrderRequest(s.getId(), "DIESEL_50", bd("1000"), bd("20"), null, null);
        assertThrows(IllegalStateException.class, () -> purchasing.create(T, req, ALICE));
    }

    @Test @DisplayName("the engine's outcome places or returns the order, and a repeat does nothing")
    void outcomeApplied() {
        FuelSupplier s = approvedSupplier(); FuelPurchaseOrder p = draft(s.getId());
        p.submitForApproval(UUID.randomUUID());
        when(orders.findActiveByIdForUpdate(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(p));
        purchasing.applyApprovalOutcome(T, p.getId(), "APPROVED");
        assertEquals("ORDERED", p.getStatus());
        purchasing.applyApprovalOutcome(T, p.getId(), "REJECTED");
        assertEquals("ORDERED", p.getStatus());
        verify(orders, times(1)).save(any());
    }

    @Test @DisplayName("an outcome for an order of another company or a missing order is ignored")
    void outcomeMissing() {
        when(orders.findActiveByIdForUpdate(any(TenantId.class), any(UUID.class))).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> purchasing.applyApprovalOutcome(T, UUID.randomUUID(), "APPROVED"));
        verify(orders, never()).save(any());
    }

    // ── stock adjustments ────────────────────────────────────────────────────
    @Test @DisplayName("below the threshold, or with no threshold, the adjustment posts exactly as before")
    void adjustDirect() {
        when(stock.adjust(any(TenantId.class), any(UUID.class), any())).thenReturn(movement());
        AdjustOutcome none = adjustments.adjust(T, TANK, new AdjustStockRequest(bd("-100000"), "Stocktake loss"), ALICE);
        assertFalse(none.pendingApproval());
        cfg.updateControls(false, bd("500"));
        assertFalse(adjustments.adjust(T, TANK, new AdjustStockRequest(bd("499"), "Found fuel"), ALICE).pendingApproval());
        verify(stock, times(2)).adjust(any(TenantId.class), any(UUID.class), any());
        verifyNoInteractions(requests);
    }

    @Test @DisplayName("at or above the threshold nothing moves; a request is held")
    void adjustHeld() {
        cfg.updateControls(false, bd("500"));
        FuelTank tank = mock(FuelTank.class); when(tank.getName()).thenReturn("Tank 1");
        when(tanks.findActiveById(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(tank));
        AdjustOutcome out = adjustments.adjust(T, TANK, new AdjustStockRequest(bd("-500"), "Stocktake loss"), ALICE);
        assertTrue(out.pendingApproval());
        assertEquals("PENDING", out.request().status());
        verify(stock, never()).adjust(any(TenantId.class), any(UUID.class), any());
    }

    @Test @DisplayName("a held adjustment for a tank of another company is refused")
    void adjustHeldOtherTenantTank() {
        cfg.updateControls(false, bd("500"));
        when(tanks.findActiveById(any(TenantId.class), any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> adjustments.adjust(T, TANK, new AdjustStockRequest(bd("900"), "Found fuel"), ALICE));
        verify(requests, never()).save(any());
    }

    @Test @DisplayName("the requester cannot approve their own adjustment and stock does not move")
    void selfApprovalRefused() {
        FuelStockAdjustmentRequest r = FuelStockAdjustmentRequest.create(T, TANK, bd("-800"), "Stocktake loss", ALICE);
        when(requests.findByIdForUpdate(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(r));
        assertThrows(IllegalStateException.class, () -> adjustments.approve(T, r.getId(), ALICE));
        verify(stock, never()).adjust(any(TenantId.class), any(UUID.class), any());
        assertTrue(r.isPending());
    }

    @Test @DisplayName("a second person's approval posts the adjustment once")
    void approvedPostsOnce() {
        FuelStockAdjustmentRequest r = FuelStockAdjustmentRequest.create(T, TANK, bd("-800"), "Stocktake loss", ALICE);
        when(requests.findByIdForUpdate(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(r));
        when(stock.adjust(any(TenantId.class), any(UUID.class), any())).thenReturn(movement());
        when(tanks.findActiveById(any(TenantId.class), any(UUID.class))).thenReturn(Optional.empty());
        assertEquals("APPROVED", adjustments.approve(T, r.getId(), BOB).status());
        assertThrows(IllegalStateException.class, () -> adjustments.approve(T, r.getId(), BOB));
        verify(stock, times(1)).adjust(any(TenantId.class), any(UUID.class), any());
    }

    @Test @DisplayName("if the stock cannot take the adjustment, the request stays pending")
    void approvalFailsStaysPending() {
        FuelStockAdjustmentRequest r = FuelStockAdjustmentRequest.create(T, TANK, bd("-800"), "Stocktake loss", ALICE);
        when(requests.findByIdForUpdate(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(r));
        when(stock.adjust(any(TenantId.class), any(UUID.class), any())).thenThrow(new IllegalStateException("reserved"));
        assertThrows(IllegalStateException.class, () -> adjustments.approve(T, r.getId(), BOB));
        assertTrue(r.isPending());
    }

    @Test @DisplayName("a request id from another company finds nothing")
    void requestOtherTenant() {
        when(requests.findByIdForUpdate(any(TenantId.class), any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> adjustments.approve(T, UUID.randomUUID(), BOB));
    }
}
