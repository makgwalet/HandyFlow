package za.co.handyflow.platform.fuel.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FuelCustomerSiteCoordinatesTest {

    static BigDecimal bd(String s) { return new BigDecimal(s); }

    private FuelCustomerSite site() {
        return FuelCustomerSite.create(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "Pit 1", null, null, null, null, null);
    }

    @Test @DisplayName("a new site has no map position")
    void startsEmpty() {
        FuelCustomerSite s = site();
        assertNull(s.getLatitude());
        assertNull(s.getLongitude());
    }

    @Test @DisplayName("a complete position in range is kept")
    void keepsAPosition() {
        FuelCustomerSite s = site();
        s.setCoordinates(bd("-26.2041"), bd("28.0473"));
        assertEquals(bd("-26.2041"), s.getLatitude());
        assertEquals(bd("28.0473"), s.getLongitude());
    }

    @Test @DisplayName("both blank clears the position")
    void clears() {
        FuelCustomerSite s = site();
        s.setCoordinates(bd("-26.2"), bd("28.0"));
        s.setCoordinates(null, null);
        assertNull(s.getLatitude());
        assertNull(s.getLongitude());
    }

    @Test @DisplayName("half a position is refused and the old one is kept")
    void halfIsRefused() {
        FuelCustomerSite s = site();
        s.setCoordinates(bd("-26.2"), bd("28.0"));
        assertThrows(IllegalArgumentException.class, () -> s.setCoordinates(bd("-26.5"), null));
        assertThrows(IllegalArgumentException.class, () -> s.setCoordinates(null, bd("28.5")));
        assertEquals(bd("-26.2"), s.getLatitude());
    }

    @Test @DisplayName("out of range values are refused")
    void outOfRange() {
        FuelCustomerSite s = site();
        assertThrows(IllegalArgumentException.class, () -> s.setCoordinates(bd("90.1"), bd("0")));
        assertThrows(IllegalArgumentException.class, () -> s.setCoordinates(bd("0"), bd("-180.1")));
        s.setCoordinates(bd("90"), bd("-180"));
        assertEquals(bd("90"), s.getLatitude());
    }
}
