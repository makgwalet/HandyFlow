package za.co.handyflow.platform.fuel.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FuelWholesaleControlsTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID ALICE = UUID.randomUUID(), BOB = UUID.randomUUID();

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    // ── suppliers ────────────────────────────────────────────────────────────
    private static FuelSupplier supplier() { return FuelSupplier.create(T, "Acme", null, null, null, null); }

    @Test @DisplayName("a new supplier is approved unless the company requires approval")
    void supplierDefaultsApproved() { assertTrue(supplier().isApproved()); }

    @Test @DisplayName("a supplier waiting for approval cannot be approved by its creator, but can by someone else")
    void creatorCannotApprove() {
        FuelSupplier s = supplier(); s.requireApproval(ALICE);
        assertFalse(s.isApproved());
        assertThrows(IllegalStateException.class, () -> s.approve(ALICE));
        assertFalse(s.isApproved());
        s.approve(BOB);
        assertTrue(s.isApproved());
        assertEquals(BOB, s.getStatusChangedBy());
    }

    @Test @DisplayName("suspending needs a reason, is idempotent, and approving reinstates")
    void suspendAndReinstate() {
        FuelSupplier s = supplier();
        assertThrows(IllegalArgumentException.class, () -> s.suspend(ALICE, "no"));
        s.suspend(ALICE, "Licence expired");
        s.suspend(BOB, "Another reason");
        assertEquals("SUSPENDED", s.getApprovalStatus());
        assertEquals("Licence expired", s.getStatusReason());
        s.approve(ALICE);   // reinstating is allowed for the person who suspended
        assertTrue(s.isApproved());
        assertNull(s.getStatusReason());
    }

    // ── purchase orders ──────────────────────────────────────────────────────
    private static FuelPurchaseOrder po() { return FuelPurchaseOrder.create(T, "PO-1", UUID.randomUUID(), "DIESEL_50", bd("1000"), bd("20"), null, null, ALICE); }

    @Test @DisplayName("an order sent for approval is not placed until approved, and cannot be received meanwhile")
    void poWaitsForApproval() {
        FuelPurchaseOrder p = po();
        p.submitForApproval(UUID.randomUUID());
        assertEquals("PENDING_APPROVAL", p.getStatus());
        assertFalse(p.canReceive());
        assertTrue(p.approvalGranted());
        assertEquals("ORDERED", p.getStatus());
        assertTrue(p.canReceive());
    }

    @Test @DisplayName("a repeated or late approval outcome changes nothing")
    void poOutcomeIdempotent() {
        FuelPurchaseOrder p = po();
        assertFalse(p.approvalGranted());          // still a draft
        p.place();
        assertFalse(p.approvalGranted());
        assertFalse(p.approvalRejected("x"));
        assertEquals("ORDERED", p.getStatus());
    }

    @Test @DisplayName("a rejected order goes back to draft with a note and can be edited")
    void poRejected() {
        FuelPurchaseOrder p = po();
        p.submitForApproval(UUID.randomUUID());
        assertTrue(p.approvalRejected("Price too high"));
        assertEquals("DRAFT", p.getStatus());
        assertEquals("Price too high", p.getApprovalNote());
        p.edit("DIESEL_50", bd("1000"), bd("19"), null, null);
    }

    @Test @DisplayName("an order waiting for approval can be cancelled, and cannot be edited or sent for approval twice")
    void poPendingRules() {
        FuelPurchaseOrder p = po();
        p.submitForApproval(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> p.edit("DIESEL_50", bd("1"), bd("1"), null, null));
        assertThrows(IllegalStateException.class, () -> p.submitForApproval(UUID.randomUUID()));
        p.cancel("Changed our mind");
        assertEquals("CANCELLED", p.getStatus());
    }

    // ── adjustments ──────────────────────────────────────────────────────────
    @Test @DisplayName("the threshold applies to litres in either direction; unset means no limit")
    void thresholdBothWays() {
        FuelSettings s = FuelSettings.forTenant(T);
        assertFalse(s.adjustmentNeedsApproval(bd("999999")));
        s.updateControls(false, bd("500"));
        assertFalse(s.adjustmentNeedsApproval(bd("499.999")));
        assertTrue(s.adjustmentNeedsApproval(bd("500")));
        assertTrue(s.adjustmentNeedsApproval(bd("-750")));
        assertThrows(IllegalArgumentException.class, () -> s.updateControls(false, BigDecimal.ZERO));
        s.updateControls(true, null);
        assertTrue(s.isRequireSupplierApproval());
        assertFalse(s.adjustmentNeedsApproval(bd("1000000")));
    }

    @Test @DisplayName("the requester cannot approve or reject; another person can; a decided request is final")
    void adjustmentFourEyes() {
        FuelStockAdjustmentRequest r = FuelStockAdjustmentRequest.create(T, UUID.randomUUID(), bd("-800"), "Stocktake loss", ALICE);
        assertThrows(IllegalStateException.class, () -> r.approve(ALICE, UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> r.reject(ALICE, "Not right"));
        assertThrows(IllegalArgumentException.class, () -> r.reject(BOB, "no"));
        UUID movement = UUID.randomUUID();
        r.approve(BOB, movement);
        assertEquals("APPROVED", r.getStatus());
        assertEquals(movement, r.getMovementId());
        assertThrows(IllegalStateException.class, () -> r.approve(BOB, movement));
        assertThrows(IllegalStateException.class, () -> r.cancel(ALICE));
    }

    @Test @DisplayName("only the requester can withdraw; zero litres is refused")
    void adjustmentWithdraw() {
        FuelStockAdjustmentRequest r = FuelStockAdjustmentRequest.create(T, UUID.randomUUID(), bd("900"), "Found stock", ALICE);
        assertThrows(IllegalStateException.class, () -> r.cancel(BOB));
        r.cancel(ALICE);
        assertEquals("CANCELLED", r.getStatus());
        assertThrows(IllegalArgumentException.class, () -> FuelStockAdjustmentRequest.create(T, UUID.randomUUID(), BigDecimal.ZERO, "Nothing", ALICE));
    }
}
