package za.co.handyflow.platform.fleet.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The engine-hour meter lives on the vehicle, the hourly operating rate on the asset profile. */
class EquipmentRulesTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private static Vehicle tractor() {
        return Vehicle.create(TenantId.of(UUID.randomUUID()), "FARM 001 GP", "John Deere", "6120M", 2021, "Green", null, "TRACTOR", "DIESEL",
                null, null, null, null, bd("200"), 10000, null, null, null);
    }

    @Test
    @DisplayName("a new vehicle's meter starts at zero")
    void meterStartsAtZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(tractor().getEngineHours()));
    }

    @Test
    @DisplayName("the meter moves up, may stay the same, and never goes back")
    void meterOnlyCountsUp() {
        Vehicle v = tractor();
        v.updateEngineHours(bd("1234.5"));
        v.updateEngineHours(bd("1234.5"));
        assertThrows(IllegalArgumentException.class, () -> v.updateEngineHours(bd("1234.4")));
        assertEquals(0, bd("1234.5").compareTo(v.getEngineHours()));
    }

    @Test
    @DisplayName("a new asset profile has no operating rate until one is set")
    void profileStartsWithoutARate() {
        assertNull(AssetProfile.create(TenantId.of(UUID.randomUUID()), UUID.randomUUID()).getOperatingRatePerHour());
    }

    @Test
    @DisplayName("the rate can be set, changed, cleared with null, and be zero")
    void rateSetsAndClears() {
        AssetProfile p = AssetProfile.create(TenantId.of(UUID.randomUUID()), UUID.randomUUID());
        p.changeOperatingRate(bd("85.5"));
        assertEquals(0, bd("85.5").compareTo(p.getOperatingRatePerHour()));
        p.changeOperatingRate(BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(p.getOperatingRatePerHour()));
        p.changeOperatingRate(null);
        assertNull(p.getOperatingRatePerHour());
    }

    @Test
    @DisplayName("a negative rate is refused and the profile keeps what it had")
    void negativeRateRefused() {
        AssetProfile p = AssetProfile.create(TenantId.of(UUID.randomUUID()), UUID.randomUUID());
        p.changeOperatingRate(bd("50"));
        assertThrows(IllegalArgumentException.class, () -> p.changeOperatingRate(bd("-0.01")));
        assertEquals(0, bd("50").compareTo(p.getOperatingRatePerHour()));
    }
}
