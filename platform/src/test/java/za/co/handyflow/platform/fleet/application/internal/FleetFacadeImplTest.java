package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.fleet.application.FleetFacade.EquipmentSummary;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FleetFacadeImplTest {

    @Mock VehicleRepository vehicleRepository;
    static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private FleetFacadeImpl facade() { return new FleetFacadeImpl(vehicleRepository); }

    // Each vehicle is fully stubbed before it goes into a when(...): Mockito cannot stub inside an open stubbing.
    private Vehicle vehicle(UUID id, String registration, VehicleStatus status, String hours, String rate) {
        Vehicle v = mock(Vehicle.class);
        when(v.getId()).thenReturn(id);
        when(v.getRegistration()).thenReturn(registration);
        when(v.getMake()).thenReturn("John Deere");
        when(v.getModel()).thenReturn("6120M");
        when(v.getVehicleType()).thenReturn("TRACTOR");
        when(v.getStatus()).thenReturn(status);
        when(v.getEngineHours()).thenReturn(hours == null ? null : new BigDecimal(hours));
        when(v.getOperatingRatePerHour()).thenReturn(rate == null ? null : new BigDecimal(rate));
        return v;
    }

    @Test
    @DisplayName("a vehicle is shown as a machine: its details, meter and operating rate")
    void findsOne() {
        UUID id = UUID.randomUUID();
        Vehicle v = vehicle(id, "FARM 001 GP", VehicleStatus.AVAILABLE, "1234.5", "85.5");
        when(vehicleRepository.findActiveById(eq(TENANT), eq(id))).thenReturn(Optional.of(v));

        EquipmentSummary s = facade().findEquipment(TENANT, id).orElseThrow();

        assertEquals(id, s.id());
        assertEquals("FARM 001 GP", s.registration());
        assertEquals("John Deere", s.make());
        assertEquals("6120M", s.model());
        assertEquals("TRACTOR", s.vehicleType());
        assertEquals(0, new BigDecimal("1234.5").compareTo(s.engineHours()));
        assertEquals(0, new BigDecimal("85.5").compareTo(s.operatingRatePerHour()));
    }

    @Test
    @DisplayName("a vehicle with no rate set shows null, never a guess")
    void noRateIsNull() {
        UUID id = UUID.randomUUID();
        Vehicle v = vehicle(id, "FARM 002 GP", VehicleStatus.AVAILABLE, null, null);
        when(vehicleRepository.findActiveById(eq(TENANT), eq(id))).thenReturn(Optional.of(v));

        EquipmentSummary s = facade().findEquipment(TENANT, id).orElseThrow();

        assertNull(s.operatingRatePerHour());
        assertNull(s.engineHours());
    }

    @Test
    @DisplayName("an unknown or deleted vehicle is empty")
    void unknown() {
        assertTrue(facade().findEquipment(TENANT, UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("the list leaves out retired vehicles and is sorted by registration, ignoring case")
    void listsSortedWithoutRetired() {
        Vehicle b = vehicle(UUID.randomUUID(), "b-harvester", VehicleStatus.AVAILABLE, null, "120");
        Vehicle a = vehicle(UUID.randomUUID(), "A-TRACTOR", VehicleStatus.ON_TRIP, null, "85");
        Vehicle old = vehicle(UUID.randomUUID(), "OLD-ONE", VehicleStatus.RETIRED, null, "10");
        Page<Vehicle> page = new PageImpl<>(List.of(b, old, a));
        when(vehicleRepository.findAllActive(eq(TENANT), any())).thenReturn(page);

        List<EquipmentSummary> list = facade().listEquipment(TENANT);

        assertEquals(List.of("A-TRACTOR", "b-harvester"), list.stream().map(EquipmentSummary::registration).toList());
    }
}
