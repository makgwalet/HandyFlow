package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelStockMovement;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.FuelReservationRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelStockMovementRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelStockLedgerTest {

    @Mock FuelStockMovementRepository movements;
    @Mock FuelReservationRepository reservations;
    FuelStockLedger ledger;
    FuelTank tank;
    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void setUp() {
        ledger = new FuelStockLedger(movements, reservations);
        tank = FuelTank.create(TENANT, "Diesel 1", "DIESEL", bd("10000"), "Yard", null);
        tank.addStock(bd("5000"), bd("20"));
        when(movements.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void reserved(String litres) {
        when(reservations.sumLiveForTank(any(), any(), any())).thenReturn(bd(litres));
    }

    @Test @DisplayName("post() records the movement with the tank's balance AFTER the change")
    void postUsesBalanceAfter() {
        tank.removeStock(bd("300"));
        FuelStockMovement m = ledger.post(tank, FuelStockMovement.Type.DISPATCH, bd("-300"), "DISPATCH", UUID.randomUUID(), "Tractor");
        assertEquals(0, bd("4700").compareTo(m.getBalanceAfter()));
        assertEquals(0, bd("-300").compareTo(m.getLitresDelta()));
        assertEquals(tank.getId(), m.getTankId());
    }

    @Test @DisplayName("a zero movement is never recorded")
    void zeroRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> ledger.post(tank, FuelStockMovement.Type.ADJUSTMENT, BigDecimal.ZERO, "ADJUSTMENT", UUID.randomUUID(), "x"));
    }

    @Test @DisplayName("with no reservations nothing changes for existing tenants")
    void noReservationsNoEffect() {
        reserved("0");
        assertDoesNotThrow(() -> ledger.requireUnreserved(tank, bd("5000"), BigDecimal.ZERO));
    }

    @Test @DisplayName("fuel reserved for others cannot be taken, free fuel can")
    void reservedFuelProtected() {
        reserved("4000");                                           // 4000 of 5000 reserved
        assertDoesNotThrow(() -> ledger.requireUnreserved(tank, bd("1000"), BigDecimal.ZERO));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ledger.requireUnreserved(tank, bd("1000.01"), BigDecimal.ZERO));
        assertTrue(e.getMessage().contains("reserved"));
    }

    @Test @DisplayName("a delivery may use its own reservation but not other people's")
    void ownReservationUsable() {
        reserved("4000");                                           // 3000 own + 1000 someone else's
        assertDoesNotThrow(() -> ledger.requireUnreserved(tank, bd("4000"), bd("3000")));   // free 4000
        assertThrows(IllegalStateException.class, () -> ledger.requireUnreserved(tank, bd("4000.01"), bd("3000")));
    }
}
