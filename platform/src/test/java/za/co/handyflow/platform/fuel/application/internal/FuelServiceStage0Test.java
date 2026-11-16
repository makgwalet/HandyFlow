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
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelReceipt;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelServiceStage0Test {

    @Mock FuelTankRepository tankRepository;
    @Mock FuelSupplierRepository supplierRepository;
    @Mock FuelReceiptRepository receiptRepository;
    @Mock FuelDispatchRepository dispatchRepository;
    @Mock DipReadingRepository dipRepository;
    @Mock FuelDeliveryRepository deliveryRepository;
    @Mock ReceiptNumberGenerator receiptNumberGenerator;
    @Mock za.co.handyflow.platform.notifications.application.internal.NotificationService notificationService;
    @Mock CrmFacade crmFacade;
    @Mock FuelStockLedger ledger;
    @Mock FuelCustomerAccountRepository accountRepository;
    @Mock FuelCustomerSiteRepository siteRepository;
    @Mock FuelOrderFulfilment orderFulfilment;
    @Mock FuelPurchasingService purchasing;
    @InjectMocks FuelService service;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static final TenantId OTHER  = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    FuelTank tank;
    FuelDelivery delivery;

    @BeforeEach
    void setUp() {
        tank = FuelTank.create(TENANT, "Diesel 1", "DIESEL", bd("10000"), "Yard", null);
        tank.addStock(bd("5000"), bd("20.00"));
        delivery = FuelDelivery.create(TENANT, UUID.randomUUID(), null, Map.of(), "DIESEL",
                bd("1000"), bd("22.50"), Instant.now(), null, null);
        when(tankRepository.findActiveByIdForUpdate(any(), any())).thenReturn(Optional.empty());
        when(tankRepository.findActiveByIdForUpdate(eq(TENANT), any())).thenReturn(Optional.of(tank));
        when(tankRepository.findActiveById(eq(TENANT), any())).thenReturn(Optional.of(tank));
        when(deliveryRepository.findActiveByIdForUpdate(eq(TENANT), any())).thenReturn(Optional.of(delivery));
        when(receiptNumberGenerator.generate(any())).thenReturn("FR-000001");
        when(crmFacade.customerExists(any(), any())).thenReturn(true);
    }

    private CompleteDeliveryRequest completeReq(String litres) {
        return new CompleteDeliveryRequest(bd(litres), "P Botha", null, null, null, false, null, null, null);
    }

    @Test @DisplayName("completing a delivery twice deducts the tank once and refuses the second call")
    void doubleCompleteDeductsOnce() {
        service.completeDelivery(TENANT, UUID.randomUUID(), completeReq("1000"));
        assertEquals(0, bd("4000").compareTo(tank.getCurrentLitres()));
        assertThrows(IllegalStateException.class,
                () -> service.completeDelivery(TENANT, UUID.randomUUID(), completeReq("1000")));
        assertEquals(0, bd("4000").compareTo(tank.getCurrentLitres()), "second call must not touch the tank");
        verify(receiptNumberGenerator, times(1)).generate(any());
    }

    @Test @DisplayName("a cancelled delivery cannot be completed and the tank is untouched")
    void cancelledNotCompleted() {
        delivery.cancel();
        assertThrows(IllegalStateException.class,
                () -> service.completeDelivery(TENANT, UUID.randomUUID(), completeReq("500")));
        assertEquals(0, bd("5000").compareTo(tank.getCurrentLitres()));
    }

    @Test @DisplayName("another tenant cannot complete, cancel or reverse this tenant's delivery")
    void tenantIsolationOnDeliveries() {
        UUID id = UUID.randomUUID();
        when(deliveryRepository.findActiveByIdForUpdate(eq(OTHER), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.completeDelivery(OTHER, id, completeReq("100")));
        assertThrows(ResourceNotFoundException.class, () -> service.cancelDelivery(OTHER, id));
        assertThrows(ResourceNotFoundException.class,
                () -> service.reverseDelivery(OTHER, id, new ReverseRequest("Wrong tank"), USER));
        assertEquals(0, bd("5000").compareTo(tank.getCurrentLitres()));
    }

    @Test @DisplayName("reversing a completed delivery restores the stock and marks it REVERSED")
    void reverseDeliveryRestoresStock() {
        service.completeDelivery(TENANT, UUID.randomUUID(), completeReq("1000"));
        DeliveryResponse r = service.reverseDelivery(TENANT, UUID.randomUUID(), new ReverseRequest("Wrong tank"), USER);
        assertEquals("REVERSED", r.status());
        assertEquals(0, bd("5000").compareTo(tank.getCurrentLitres()));
        assertThrows(IllegalStateException.class,
                () -> service.reverseDelivery(TENANT, UUID.randomUUID(), new ReverseRequest("Again please"), USER));
        assertEquals(0, bd("5000").compareTo(tank.getCurrentLitres()));
    }

    @Test @DisplayName("a scheduled delivery cannot be reversed")
    void reverseNeedsDelivered() {
        assertThrows(IllegalStateException.class,
                () -> service.reverseDelivery(TENANT, UUID.randomUUID(), new ReverseRequest("Not yet done"), USER));
    }

    @Test @DisplayName("dispatch then cancel a scheduled delivery follows the state machine")
    void dispatchAndCancel() {
        assertEquals("IN_TRANSIT", service.dispatchDelivery(TENANT, UUID.randomUUID(),
                new DispatchDeliveryRequest("Sipho", "CA 123")).status());
        assertEquals("CANCELLED", service.cancelDelivery(TENANT, UUID.randomUUID()).status());
        assertThrows(IllegalStateException.class, () -> service.dispatchDelivery(TENANT, UUID.randomUUID(),
                new DispatchDeliveryRequest("Sipho", "CA 123")));
    }

    @Test @DisplayName("reversing a receipt is refused when the fuel has already been used")
    void reverseReceiptNeedsStock() {
        FuelReceipt receipt = FuelReceipt.create(TENANT, UUID.randomUUID(), null, bd("6000"), bd("20"),
                Instant.now(), null, null, bd("0"), bd("6000"));
        when(receiptRepository.findActiveById(eq(TENANT), any())).thenReturn(Optional.of(receipt));
        assertThrows(IllegalStateException.class,
                () -> service.reverseReceipt(TENANT, UUID.randomUUID(), new ReverseRequest("Entered twice"), USER));
        assertFalse(receipt.isReversed());
        assertEquals(0, bd("5000").compareTo(tank.getCurrentLitres()));
    }

    @Test @DisplayName("reversing a receipt removes its litres from the tank")
    void reverseReceiptRemovesStock() {
        FuelReceipt receipt = FuelReceipt.create(TENANT, UUID.randomUUID(), null, bd("2000"), bd("20"),
                Instant.now(), null, null, bd("3000"), bd("5000"));
        when(receiptRepository.findActiveById(eq(TENANT), any())).thenReturn(Optional.of(receipt));
        ReceiptResponse r = service.reverseReceipt(TENANT, UUID.randomUUID(), new ReverseRequest("Entered twice"), USER);
        assertNotNull(r.reversedAt());
        assertEquals(0, bd("3000").compareTo(tank.getCurrentLitres()));
    }

    @Test @DisplayName("dispatching to a customer that is not this tenant's is refused before any fuel moves")
    void dispatchRejectsForeignCustomer() {
        UUID foreign = UUID.randomUUID();
        when(crmFacade.customerExists(TENANT, foreign)).thenReturn(false);
        DispatchFuelRequest req = new DispatchFuelRequest(bd("100"), null, Instant.now(), null, null, foreign, "X", null, null, null, null);
        assertThrows(ResourceNotFoundException.class, () -> service.dispatchFuel(TENANT, UUID.randomUUID(), req, USER));
        assertEquals(0, bd("5000").compareTo(tank.getCurrentLitres()));
        verify(dispatchRepository, never()).save(any());
    }

    @Test @DisplayName("receiving from a supplier that is not this tenant's is refused")
    void receiveRejectsForeignSupplier() {
        UUID foreign = UUID.randomUUID();
        when(supplierRepository.findActiveById(eq(TENANT), any())).thenReturn(Optional.empty());
        ReceiveFuelRequest req = new ReceiveFuelRequest(bd("100"), bd("20"), Instant.now(), foreign, null, null, null);
        assertThrows(ResourceNotFoundException.class, () -> service.receiveFuel(TENANT, UUID.randomUUID(), req));
        assertEquals(0, bd("5000").compareTo(tank.getCurrentLitres()));
    }

    @Test @DisplayName("Stage 3: retrying the same request id returns the first result and deducts once; a different id is refused")
    void idempotentCompletion() {
        UUID key = UUID.randomUUID();
        CompleteDeliveryRequest first = new CompleteDeliveryRequest(bd("1000"), "P Botha", null, null, null, false, null, null, key);
        service.completeDelivery(TENANT, UUID.randomUUID(), first);
        assertEquals(0, bd("4000").compareTo(tank.getCurrentLitres()));
        assertDoesNotThrow(() -> service.completeDelivery(TENANT, UUID.randomUUID(), first));
        assertEquals(0, bd("4000").compareTo(tank.getCurrentLitres()), "retry must not deduct again");
        verify(receiptNumberGenerator, times(1)).generate(any());
        CompleteDeliveryRequest other = new CompleteDeliveryRequest(bd("1000"), "P Botha", null, null, null, false, null, null, UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> service.completeDelivery(TENANT, UUID.randomUUID(), other));
        assertEquals(0, bd("4000").compareTo(tank.getCurrentLitres()));
    }

    // ── depot limits on receipts, dispatches and deliveries ──────────────────

    @Test @DisplayName("receipts, dispatches and deliveries ask only for the user's tanks, an empty set returns nothing without a query, and no limit is unchanged")
    void listsFilteredByDepotLimit() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 50);
        java.util.Set<UUID> ids = java.util.Set.of(UUID.randomUUID());
        var none = new org.springframework.data.domain.PageImpl<>(java.util.List.<FuelReceipt>of());
        when(receiptRepository.findActiveForTanks(TENANT, ids, pageable)).thenReturn(none);
        when(dispatchRepository.findActiveForTanks(TENANT, ids, pageable)).thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
        when(deliveryRepository.findActiveForTanks(TENANT, ids, pageable)).thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
        when(deliveryRepository.findByStatusForTanks(TENANT, "SCHEDULED", ids, pageable)).thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
        service.getReceipts(TENANT, ids, pageable); service.getDispatches(TENANT, ids, pageable); service.getDeliveries(TENANT, null, ids, pageable); service.getDeliveries(TENANT, "scheduled", ids, pageable);
        verify(receiptRepository).findActiveForTanks(TENANT, ids, pageable); verify(dispatchRepository).findActiveForTanks(TENANT, ids, pageable);
        verify(deliveryRepository).findActiveForTanks(TENANT, ids, pageable); verify(deliveryRepository).findByStatusForTanks(TENANT, "SCHEDULED", ids, pageable);
        verify(receiptRepository, never()).findAllActive(any(), any()); verify(dispatchRepository, never()).findAllActive(any(), any()); verify(deliveryRepository, never()).findAllActive(any(), any());

        assertTrue(service.getReceipts(TENANT, java.util.Set.of(), pageable).isEmpty());
        assertTrue(service.getDispatches(TENANT, java.util.Set.of(), pageable).isEmpty());
        assertTrue(service.getDeliveries(TENANT, null, java.util.Set.of(), pageable).isEmpty());

        when(receiptRepository.findAllActive(TENANT, pageable)).thenReturn(none);
        service.getReceipts(TENANT, pageable);
        verify(receiptRepository).findAllActive(TENANT, pageable);
    }

    @Test @DisplayName("the usage and margin reports leave out other depots' tanks")
    void reportsFilteredByDepotLimit() {
        FuelTank mine = mock(FuelTank.class); UUID mineId = UUID.randomUUID(); when(mine.getId()).thenReturn(mineId);
        FuelDispatch mineD = mock(FuelDispatch.class), otherD = mock(FuelDispatch.class);
        UUID otherId = UUID.randomUUID();
        when(mineD.getTankId()).thenReturn(mineId); when(otherD.getTankId()).thenReturn(otherId);
        when(mineD.getLitresDispensed()).thenReturn(new java.math.BigDecimal("100")); when(otherD.getLitresDispensed()).thenReturn(new java.math.BigDecimal("900"));
        when(dispatchRepository.findByTenantAndDispatchedAtBetween(any(), any(), any())).thenReturn(java.util.List.of(mineD, otherD));
        when(deliveryRepository.findDeliveredBetween(any(), any(), any())).thenReturn(java.util.List.of());
        var all = service.generateMarginReport(TENANT, null, null);
        assertEquals(2, all.lines().size());
        var limited = service.generateMarginReport(TENANT, null, null, java.util.Set.of(mineId));
        assertEquals(1, limited.lines().size());
        assertEquals(mineId, limited.lines().get(0).tankId());
    }
}
