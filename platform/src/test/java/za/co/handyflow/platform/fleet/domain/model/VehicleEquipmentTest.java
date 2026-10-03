package za.co.handyflow.platform.fleet.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VehicleEquipmentTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private static Vehicle tractor() {
        return Vehicle.create(TenantId.of(UUID.randomUUID()), "FARM 001 GP", "John Deere", "6120M", 2021, "Green", null, "TRACTOR", "DIESEL",
                null, null, null, null, bd("200"), 10000, null, null, null);
    }

    @Test
    @DisplayName("a new vehicle has no meter reading and no operating rate until someone sets them")
    void startsEmpty() {
        Vehicle v = tractor();
        assertNull(v.getEngineHours());
        assertNull(v.getOperatingRatePerHour());
    }

    @Test
    @DisplayName("the meter and the operating rate are set together")
    void sets() {
        Vehicle v = tractor();

        v.updateEquipment(bd("1234.5"), bd("85.5"));

        assertEquals(0, bd("1234.5").compareTo(v.getEngineHours()));
        assertEquals(0, bd("85.5").compareTo(v.getOperatingRatePerHour()));
    }

    @Test
    @DisplayName("either may be cleared with null, and zero is allowed")
    void clearsAndZero() {
        Vehicle v = tractor();
        v.updateEquipment(bd("100"), bd("50"));

        v.updateEquipment(null, null);
        assertNull(v.getEngineHours());
        assertNull(v.getOperatingRatePerHour());

        v.updateEquipment(BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(v.getOperatingRatePerHour()));
    }

    @Test
    @DisplayName("negative values are refused and the vehicle keeps what it had")
    void refusesNegatives() {
        Vehicle v = tractor();
        v.updateEquipment(bd("100"), bd("50"));

        assertThrows(IllegalArgumentException.class, () -> v.updateEquipment(bd("-1"), bd("50")));
        assertThrows(IllegalArgumentException.class, () -> v.updateEquipment(bd("100"), bd("-0.01")));

        assertEquals(0, bd("100").compareTo(v.getEngineHours()));
        assertEquals(0, bd("50").compareTo(v.getOperatingRatePerHour()));
    }
}
