package za.co.handyflow.platform.fuel.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FuelSettingsTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test @DisplayName("a new tenant has no rates, a 2-hour typical trip and a 2% delivery tolerance")
    void defaults() {
        FuelSettings s = FuelSettings.forTenant(T);
        assertTrue(s.ratesUnset());
        assertEquals(0, bd("2").compareTo(s.getDefaultTripHours()));
        assertEquals(0, bd("0.02").compareTo(s.poToleranceFraction()));
    }

    @Test @DisplayName("rates, overhead, trip hours and tolerance are range-checked")
    void validation() {
        FuelSettings s = FuelSettings.forTenant(T);
        assertThrows(IllegalArgumentException.class, () -> s.update(bd("-1"), bd("0"), bd("0"), bd("0"), bd("2"), bd("2")));
        assertThrows(IllegalArgumentException.class, () -> s.update(bd("0"), bd("0"), bd("0"), bd("101"), bd("2"), bd("2")));
        assertThrows(IllegalArgumentException.class, () -> s.update(bd("0"), bd("0"), bd("0"), bd("0"), bd("0"), bd("2")));
        assertThrows(IllegalArgumentException.class, () -> s.update(bd("0"), bd("0"), bd("0"), bd("0"), bd("25"), bd("2")));
        assertThrows(IllegalArgumentException.class, () -> s.update(bd("0"), bd("0"), bd("0"), bd("0"), bd("2"), bd("11")));
        s.update(bd("150"), bd("6.5"), bd("40"), bd("3"), bd("2.5"), bd("5"));
        assertFalse(s.ratesUnset());
        assertEquals(0, bd("0.05").compareTo(s.poToleranceFraction()));
    }

    @Test @DisplayName("blank trip hours and tolerance fall back to the defaults rather than zero")
    void nullsFallBack() {
        FuelSettings s = FuelSettings.forTenant(T);
        s.update(null, null, null, null, null, null);
        assertTrue(s.ratesUnset());
        assertEquals(0, bd("2").compareTo(s.getDefaultTripHours()));
        assertEquals(0, bd("2").compareTo(s.getPoOverTolerancePct()));
    }

    @Test @DisplayName("a site distance must be 0 to 5000 km, or cleared")
    void siteDistance() {
        FuelCustomerSite site = FuelCustomerSite.create(T, UUID.randomUUID(), "Pit 1", null, null, null, null, null);
        site.setDistanceKm(bd("42.5"));
        assertEquals(0, bd("42.5").compareTo(site.getDistanceKm()));
        assertThrows(IllegalArgumentException.class, () -> site.setDistanceKm(bd("-1")));
        assertThrows(IllegalArgumentException.class, () -> site.setDistanceKm(bd("5001")));
        site.setDistanceKm(null);
        assertNull(site.getDistanceKm());
    }

    @Test @DisplayName("the emergency contact needs a name and a number together, and a number a driver can ring")
    void emergencyContact() {
        FuelSettings s = FuelSettings.forTenant(T);
        assertNull(s.getEmergencyContactPhone());
        s.updateEmergencyContact(" Control room ", " +27 11 555 0100 ");
        assertEquals("Control room", s.getEmergencyContactName());
        assertEquals("+27 11 555 0100", s.getEmergencyContactPhone());
        assertThrows(IllegalArgumentException.class, () -> s.updateEmergencyContact("Control room", null));
        assertThrows(IllegalArgumentException.class, () -> s.updateEmergencyContact(null, "0115550100"));
        assertThrows(IllegalArgumentException.class, () -> s.updateEmergencyContact("Control room", "call me"));
        assertEquals("Control room", s.getEmergencyContactName(), "a refused change leaves the saved contact alone");
        s.updateEmergencyContact("", " ");
        assertNull(s.getEmergencyContactName());
        assertNull(s.getEmergencyContactPhone());
    }
}
