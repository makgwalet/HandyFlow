package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelPurchaseOrder;
import za.co.handyflow.platform.fuel.domain.model.FuelReceipt;
import za.co.handyflow.platform.fuel.domain.repository.FuelPurchaseOrderRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelReceiptRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelSupplierRepository;
import za.co.handyflow.platform.fuel.dto.PurchaseOrderMatchResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelPurchasingServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID SUPPLIER = UUID.randomUUID();

    @Mock FuelPurchaseOrderRepository orders;
    @Mock FuelReceiptRepository receipts;
    @Mock FuelSupplierRepository suppliers;
    @Mock PurchaseOrderNumberGenerator numbers;
    @Mock FuelSettingsService settings;
    @InjectMocks FuelPurchasingService service;

    @org.junit.jupiter.api.BeforeEach
    void defaults() { when(settings.poToleranceFraction(any())).thenReturn(bd("0.02")); }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private FuelPurchaseOrder placed(String litres, String price) {
        FuelPurchaseOrder p = FuelPurchaseOrder.create(T, "PO-2026-00001", SUPPLIER, "DIESEL_50", bd(litres), bd(price), null, null, null);
        p.place();
        return p;
    }

    @Test @DisplayName("a draft can be edited; once placed it is locked")
    void editLocked() {
        FuelPurchaseOrder p = FuelPurchaseOrder.create(T, "PO-1", SUPPLIER, "DIESEL_50", bd("1000"), bd("20"), null, null, null);
        p.edit("DIESEL_50", bd("2000"), bd("20"), null, null);
        p.place();
        assertThrows(IllegalStateException.class, () -> p.edit("DIESEL_50", bd("3000"), bd("20"), null, null));
    }

    @Test @DisplayName("receiving is refused on a draft")
    void draftRefused() {
        FuelPurchaseOrder p = FuelPurchaseOrder.create(T, "PO-1", SUPPLIER, "DIESEL_50", bd("1000"), bd("20"), null, null, null);
        when(orders.findActiveByIdForUpdate(T, p.getId())).thenReturn(Optional.of(p));
        assertThrows(IllegalStateException.class, () -> service.checkReceivable(T, p.getId(), SUPPLIER, "DIESEL_50", bd("100")));
    }

    @Test @DisplayName("receiving from a different supplier is refused")
    void wrongSupplier() {
        FuelPurchaseOrder p = placed("1000", "20");
        when(orders.findActiveByIdForUpdate(T, p.getId())).thenReturn(Optional.of(p));
        when(receipts.sumReceivedForPo(T, p.getId())).thenReturn(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> service.checkReceivable(T, p.getId(), UUID.randomUUID(), "DIESEL_50", bd("100")));
    }

    @Test @DisplayName("receiving a different fuel type is refused")
    void wrongFuel() {
        FuelPurchaseOrder p = placed("1000", "20");
        when(orders.findActiveByIdForUpdate(T, p.getId())).thenReturn(Optional.of(p));
        when(receipts.sumReceivedForPo(T, p.getId())).thenReturn(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> service.checkReceivable(T, p.getId(), SUPPLIER, "PETROL_95", bd("100")));
    }

    @Test @DisplayName("up to 2% over is accepted, beyond that is refused")
    void tolerance() {
        FuelPurchaseOrder p = placed("1000", "20");
        when(orders.findActiveByIdForUpdate(T, p.getId())).thenReturn(Optional.of(p));
        when(receipts.sumReceivedForPo(T, p.getId())).thenReturn(bd("900"));
        assertDoesNotThrow(() -> service.checkReceivable(T, p.getId(), SUPPLIER, "DIESEL_50", bd("120")));
        assertThrows(IllegalStateException.class, () -> service.checkReceivable(T, p.getId(), SUPPLIER, "DIESEL_50", bd("121")));
    }

    @Test @DisplayName("status follows litres received: none, part, full (within tolerance), and back on reversal")
    void statusFollowsReceipts() {
        FuelPurchaseOrder p = placed("1000", "20");
        p.applyReceived(bd("400")); assertEquals("PART_RECEIVED", p.getStatus());
        p.applyReceived(bd("980")); assertEquals("RECEIVED", p.getStatus());
        p.applyReceived(bd("400")); assertEquals("PART_RECEIVED", p.getStatus());
        p.applyReceived(BigDecimal.ZERO); assertEquals("ORDERED", p.getStatus());
    }

    @Test @DisplayName("a cancelled order stays cancelled whatever the receipts say")
    void cancelledSticks() {
        FuelPurchaseOrder p = placed("1000", "20");
        p.cancel("Supplier out of stock");
        p.applyReceived(bd("1000"));
        assertEquals("CANCELLED", p.getStatus());
    }

    @Test @DisplayName("cancelling needs a reason and is refused once fuel has arrived")
    void cancelRules() {
        FuelPurchaseOrder p = placed("1000", "20");
        assertThrows(IllegalArgumentException.class, () -> p.cancel(" "));
        when(orders.findActiveById(T, p.getId())).thenReturn(Optional.of(p));
        when(receipts.sumReceivedForPo(T, p.getId())).thenReturn(bd("10"));
        assertThrows(IllegalStateException.class, () -> service.cancel(T, p.getId(), "mistake"));
    }

    @Test @DisplayName("the match report shows price variance and ignores reversed receipts in totals")
    void matchReport() {
        FuelPurchaseOrder p = placed("1000", "20.0000");
        when(orders.findActiveById(T, p.getId())).thenReturn(Optional.of(p));
        when(suppliers.findActiveById(T, SUPPLIER)).thenReturn(Optional.empty());
        FuelReceipt ok = FuelReceipt.create(T, UUID.randomUUID(), SUPPLIER, bd("500"), bd("20.5000"), Instant.now(), "DN1", null, bd("0"), bd("500"));
        FuelReceipt gone = FuelReceipt.create(T, UUID.randomUUID(), SUPPLIER, bd("100"), bd("20.0000"), Instant.now(), "DN2", null, bd("0"), bd("100"));
        gone.reverse(UUID.randomUUID(), "wrong tank");
        when(receipts.findByPo(T, p.getId())).thenReturn(List.of(ok, gone));
        PurchaseOrderMatchResponse m = service.match(T, p.getId());
        assertEquals(0, bd("500").compareTo(m.order().litresReceived()));
        assertEquals(0, bd("-500").compareTo(m.litresVariance()));
        assertEquals(0, bd("10250.00").compareTo(m.receivedValue()));
        assertEquals(0, bd("-9750.00").compareTo(m.valueVariance()));
        assertTrue(m.receipts().get(0).priceDiffers());
        assertFalse(m.receipts().get(1).priceDiffers());
        assertTrue(m.receipts().get(1).reversed());
    }

    @Test @DisplayName("a tenant's own tolerance decides what is accepted and when an order counts as fully received")
    void tenantTolerance() {
        FuelPurchaseOrder p = placed("1000", "20");
        when(orders.findActiveByIdForUpdate(T, p.getId())).thenReturn(Optional.of(p));
        when(receipts.sumReceivedForPo(T, p.getId())).thenReturn(bd("1000"));
        when(settings.poToleranceFraction(any())).thenReturn(bd("0.05"));
        assertDoesNotThrow(() -> service.checkReceivable(T, p.getId(), SUPPLIER, "DIESEL_50", bd("50")));
        assertThrows(IllegalStateException.class, () -> service.checkReceivable(T, p.getId(), SUPPLIER, "DIESEL_50", bd("51")));
        p.applyReceived(bd("960"), bd("0.05"));
        assertEquals("RECEIVED", p.getStatus()); // 960 of 1000 is within 5%
        p.applyReceived(bd("960"), bd("0.02"));
        assertEquals("PART_RECEIVED", p.getStatus());
        p.applyReceived(bd("950"), bd("0.05"));
        assertEquals("RECEIVED", p.getStatus());
    }

    @Test @DisplayName("close short: only a part-received order, with a real reason; receiving stops and the litres stay")
    void closeShortRules() {
        FuelPurchaseOrder none = placed("1000", "20");
        assertThrows(IllegalStateException.class, () -> none.closeShort("supplier short"));   // nothing received: cancel instead
        FuelPurchaseOrder p = placed("1000", "20");
        p.applyReceived(bd("600"));
        assertThrows(IllegalArgumentException.class, () -> p.closeShort("  "));
        assertThrows(IllegalArgumentException.class, () -> p.closeShort("no"));
        p.closeShort("Supplier ran out of stock");
        assertEquals("CLOSED_SHORT", p.getStatus());
        assertFalse(p.canReceive());
        p.applyReceived(bd("1000"));                      // later receipts changes do not reopen it
        assertEquals("CLOSED_SHORT", p.getStatus());
        assertThrows(IllegalStateException.class, () -> p.cancel("x"));
    }

    @Test @DisplayName("closing short through the service re-syncs first, so a fully received order is not closed short")
    void closeShortService() {
        FuelPurchaseOrder p = placed("1000", "20");
        when(orders.findActiveByIdForUpdate(T, p.getId())).thenReturn(Optional.of(p));
        when(suppliers.findActiveById(T, SUPPLIER)).thenReturn(Optional.empty());
        when(receipts.sumReceivedForPo(T, p.getId())).thenReturn(bd("700"));
        assertEquals("CLOSED_SHORT", service.closeShort(T, p.getId(), "Supplier cannot deliver the rest").status());
        FuelPurchaseOrder full = placed("1000", "20");
        when(orders.findActiveByIdForUpdate(T, full.getId())).thenReturn(Optional.of(full));
        when(receipts.sumReceivedForPo(T, full.getId())).thenReturn(bd("995"));
        assertThrows(IllegalStateException.class, () -> service.closeShort(T, full.getId(), "Supplier cannot deliver the rest"));
    }
}
