package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelReservation;
import za.co.handyflow.platform.fuel.domain.model.FuelStockMovement;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelStockServiceTest {

    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    @Mock FuelStockMovementRepository movements;
    @Mock FuelTransferRepository transfers;
    @Mock FuelReservationRepository reservations;
    @Mock FuelStockLedger ledger;
    @Mock CrmFacade crm;
    FuelStockService service;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static final TenantId OTHER = TenantId.of(UUID.randomUUID());
    FuelTank a, b, petrol;

    static BigDecimal bd(String s) { return new BigDecimal(s); }

    private FuelTank tank(String name, String type, String litres) {
        FuelTank t = FuelTank.create(TENANT, name, type, bd("10000"), "Yard", null);
        t.addStock(bd(litres), bd("20"));
        when(tanks.findActiveByIdForUpdate(TENANT, t.getId())).thenReturn(Optional.of(t));
        return t;
    }

    @BeforeEach
    void setUp() {
        service = new FuelStockService(tanks, depots, movements, transfers, reservations, ledger, crm);
        a = tank("A", "DIESEL", "5000");
        b = tank("B", "DIESEL", "1000");
        petrol = tank("P", "PETROL", "1000");
        when(tanks.findActiveByIdForUpdate(eq(OTHER), any())).thenReturn(Optional.empty());
        when(ledger.reserved(any(), any())).thenReturn(BigDecimal.ZERO);
        when(ledger.post(any(), any(), any(), any(), any(), any())).thenAnswer(i ->
                FuelStockMovement.of(TENANT, ((FuelTank) i.getArgument(0)).getId(), i.getArgument(1), i.getArgument(2),
                        ((FuelTank) i.getArgument(0)).getCurrentLitres(), i.getArgument(3), i.getArgument(4), i.getArgument(5), null));
        when(transfers.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservations.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test @DisplayName("available to promise = physical - reserved - safety, never below zero")
    void atpFormula() {
        assertEquals(0, bd("3500").compareTo(FuelStockService.atp(bd("5000"), bd("1000"), bd("500"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(FuelStockService.atp(bd("500"), bd("400"), bd("300"))));
    }

    @Test @DisplayName("an adjustment changes the tank, writes one ledger line with the reason, and needs a valid size")
    void adjustPostsLedger() {
        MovementResponse r = service.adjust(TENANT, a.getId(), new AdjustStockRequest(bd("-120"), "Stocktake loss"));
        assertEquals(0, bd("4880").compareTo(a.getCurrentLitres()));
        assertEquals("ADJUSTMENT", r.movementType());
        verify(ledger).post(eq(a), eq(FuelStockMovement.Type.ADJUSTMENT), eq(bd("-120")), eq("ADJUSTMENT"), any(), eq("Stocktake loss"));
        assertThrows(IllegalArgumentException.class, () -> service.adjust(TENANT, a.getId(), new AdjustStockRequest(bd("-99999"), "Impossible")));
        assertThrows(IllegalArgumentException.class, () -> service.adjust(TENANT, a.getId(), new AdjustStockRequest(bd("0"), "Nothing")));
    }

    @Test @DisplayName("a transfer moves litres, posts OUT and IN lines, and refuses a different fuel type")
    void transfer() {
        service.transfer(TENANT, new TransferRequest(a.getId(), b.getId(), bd("1500"), "Balance depots"));
        assertEquals(0, bd("3500").compareTo(a.getCurrentLitres()));
        assertEquals(0, bd("2500").compareTo(b.getCurrentLitres()));
        verify(ledger).post(eq(a), eq(FuelStockMovement.Type.TRANSFER_OUT), eq(bd("-1500")), eq("TRANSFER"), any(), any());
        verify(ledger).post(eq(b), eq(FuelStockMovement.Type.TRANSFER_IN), eq(bd("1500")), eq("TRANSFER"), any(), any());
        assertThrows(IllegalArgumentException.class,
                () -> service.transfer(TENANT, new TransferRequest(a.getId(), petrol.getId(), bd("10"), "Wrong fuel")));
        assertThrows(IllegalArgumentException.class,
                () -> service.transfer(TENANT, new TransferRequest(a.getId(), a.getId(), bd("10"), "Same tank")));
    }

    @Test @DisplayName("a transfer larger than the source holds is refused and nothing moves")
    void transferTooMuch() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transfer(TENANT, new TransferRequest(b.getId(), a.getId(), bd("1000.01"), "Too much")));
        assertEquals(0, bd("1000").compareTo(b.getCurrentLitres()));
        assertEquals(0, bd("5000").compareTo(a.getCurrentLitres()));
    }

    @Test @DisplayName("reserving more than available-to-promise is refused; reserving does not change physical stock")
    void reserve() {
        a.place(null, bd("500"));                                   // safety 500 => ATP 4500
        ReservationResponse ok = service.reserve(TENANT, new ReservationRequest(a.getId(), bd("4500"), null, "DELIVERY", UUID.randomUUID(), null, null), UUID.randomUUID());
        assertEquals("ACTIVE", ok.status());
        assertEquals(0, bd("5000").compareTo(a.getCurrentLitres()), "physical stock unchanged");
        assertThrows(IllegalStateException.class,
                () -> service.reserve(TENANT, new ReservationRequest(a.getId(), bd("4500.01"), null, null, null, null, null), UUID.randomUUID()));
    }

    @Test @DisplayName("a second reservation sees the first one")
    void reserveSeesExisting() {
        when(ledger.reserved(any(), any())).thenReturn(bd("4000"));
        assertThrows(IllegalStateException.class,
                () -> service.reserve(TENANT, new ReservationRequest(a.getId(), bd("1000.01"), null, null, null, null, null), UUID.randomUUID()));
        assertDoesNotThrow(() -> service.reserve(TENANT, new ReservationRequest(a.getId(), bd("1000"), null, null, null, null, null), UUID.randomUUID()));
    }

    @Test @DisplayName("an unknown customer or a past expiry is refused")
    void reserveValidation() {
        UUID cust = UUID.randomUUID();
        when(crm.customerExists(TENANT, cust)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class,
                () -> service.reserve(TENANT, new ReservationRequest(a.getId(), bd("10"), cust, null, null, null, null), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve(TENANT, new ReservationRequest(a.getId(), bd("10"), null, null, null, Instant.now().minusSeconds(60), null), UUID.randomUUID()));
    }

    @Test @DisplayName("another tenant cannot adjust, transfer or reserve against this tenant's tanks")
    void tenantIsolation() {
        assertThrows(ResourceNotFoundException.class, () -> service.adjust(OTHER, a.getId(), new AdjustStockRequest(bd("-1"), "Not yours")));
        assertThrows(ResourceNotFoundException.class, () -> service.transfer(OTHER, new TransferRequest(a.getId(), b.getId(), bd("1"), "Not yours")));
        assertThrows(ResourceNotFoundException.class, () -> service.reserve(OTHER, new ReservationRequest(a.getId(), bd("1"), null, null, null, null, null), UUID.randomUUID()));
        assertEquals(0, bd("5000").compareTo(a.getCurrentLitres()));
    }

    @Test @DisplayName("releasing a reservation twice is refused")
    void releaseOnce() {
        FuelReservation r = FuelReservation.create(TENANT, a.getId(), bd("100"), null, null, null, null, null, null);
        when(reservations.findOwned(TENANT, r.getId())).thenReturn(Optional.of(r));
        assertEquals("RELEASED", service.releaseReservation(TENANT, r.getId()).status());
        assertThrows(IllegalStateException.class, () -> service.releaseReservation(TENANT, r.getId()));
    }

    @Test @DisplayName("safety stock must be 0..capacity")
    void placement() {
        assertThrows(IllegalArgumentException.class, () -> a.place(null, bd("-1")));
        assertThrows(IllegalArgumentException.class, () -> a.place(null, bd("10001")));
    }
}
