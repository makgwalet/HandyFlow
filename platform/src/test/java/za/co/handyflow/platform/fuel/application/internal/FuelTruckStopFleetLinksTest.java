package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import za.co.handyflow.platform.fuel.FleetVehicleLookup;
import za.co.handyflow.platform.fuel.FleetVehicleLookup.VehicleRef;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Written, not compiled or run. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelTruckStopFleetLinksTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    @Mock ObjectProvider<FleetVehicleLookup> provider;
    @Mock FleetVehicleLookup lookup;
    FuelTruckStopFleetLinks links;
    final VehicleRef truck = new VehicleRef(UUID.randomUUID(), "CA 123 456", "Volvo", "FH");

    @BeforeEach
    void setUp() {
        links = new FuelTruckStopFleetLinks(provider);
        when(provider.getIfAvailable()).thenReturn(lookup);
        when(lookup.list(T)).thenReturn(List.of(truck, new VehicleRef(UUID.randomUUID(), "GP 9", null, null)));
        when(lookup.find(T, truck.id())).thenReturn(Optional.of(truck));
    }

    @Test @DisplayName("a registration that matches exactly, ignoring case and spaces, is linked automatically")
    void autoMatch() {
        var l = links.resolve(T, null, "ca123456").orElseThrow();
        assertEquals(truck.id(), l.vehicleId());
        assertEquals("CA 123 456", l.registration());
    }

    @Test @DisplayName("no match, or two matches, means no link")
    void noOrAmbiguous() {
        assertTrue(links.resolve(T, null, "XX 1").isEmpty());
        when(lookup.list(T)).thenReturn(List.of(truck, new VehicleRef(UUID.randomUUID(), "ca123456", null, null)));
        assertTrue(links.resolve(T, null, "CA123456").isEmpty());
    }

    @Test @DisplayName("a vehicle chosen explicitly must be one of the company's and gives its registration")
    void explicitChoice() {
        assertEquals("CA 123 456", links.resolve(T, truck.id(), "typed").orElseThrow().registration());
        assertThrows(IllegalArgumentException.class, () -> links.resolve(T, UUID.randomUUID(), "typed"));
    }

    @Test @DisplayName("without Fleet an explicit choice is refused, an automatic match is just skipped, and a Fleet failure never stops a booking")
    void fleetMissingOrFailing() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> links.resolve(T, truck.id(), "x"));
        assertTrue(links.resolve(T, null, "CA 123 456").isEmpty());
        assertTrue(links.options(T).isEmpty());
        when(provider.getIfAvailable()).thenReturn(lookup);
        when(lookup.list(T)).thenThrow(new RuntimeException("slow"));
        assertTrue(links.resolve(T, null, "CA 123 456").isEmpty());
    }

    @Test @DisplayName("options list the company's vehicles with a label")
    void options() {
        when(lookup.list(T)).thenReturn(List.of(truck));
        assertEquals("CA 123 456 (Volvo FH)", links.options(T).get(0).label());
    }
}
