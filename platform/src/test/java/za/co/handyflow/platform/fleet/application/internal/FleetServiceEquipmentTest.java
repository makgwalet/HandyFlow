package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.repository.FuelFillupRepository;
import za.co.handyflow.platform.fleet.domain.repository.TripRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleServiceRepository;
import za.co.handyflow.platform.fleet.dto.EquipmentResponse;
import za.co.handyflow.platform.fleet.dto.UpdateEquipmentRequest;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FleetServiceEquipmentTest {

    @Mock VehicleRepository vehicleRepository;
    static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private FleetService service() {
        return new FleetService(vehicleRepository, mock(VehicleServiceRepository.class), mock(TripRepository.class), mock(FuelFillupRepository.class),
                mock(NotificationService.class), mock(TenantAdminRecipients.class));
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private static Vehicle tractor(String registration) {
        return Vehicle.create(TENANT, registration, "John Deere", "6120M", 2021, "Green", null, "TRACTOR", "DIESEL", null, null, null, null, bd("200"), 10000, null, null, null);
    }

    @Test
    @DisplayName("setting the equipment numbers updates the vehicle, saves it and returns them")
    void updates() {
        Vehicle v = tractor("FARM 001 GP");
        UUID id = v.getId();
        when(vehicleRepository.findActiveById(eq(TENANT), eq(id))).thenReturn(Optional.of(v));

        EquipmentResponse r = service().updateEquipment(TENANT, id, new UpdateEquipmentRequest(bd("1234.5"), bd("85.5")));

        assertEquals(0, bd("1234.5").compareTo(r.engineHours()));
        assertEquals(0, bd("85.5").compareTo(r.operatingRatePerHour()));
        assertEquals("FARM 001 GP", r.registration());
        assertEquals(0, bd("85.5").compareTo(v.getOperatingRatePerHour()));
        verify(vehicleRepository).save(eq(v));
    }

    @Test
    @DisplayName("a negative number is refused and nothing is saved")
    void refusesNegative() {
        Vehicle v = tractor("FARM 001 GP");
        UUID id = v.getId();
        when(vehicleRepository.findActiveById(eq(TENANT), eq(id))).thenReturn(Optional.of(v));

        assertThrows(IllegalArgumentException.class, () -> service().updateEquipment(TENANT, id, new UpdateEquipmentRequest(bd("100"), bd("-1"))));

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("an unknown or deleted vehicle is a 404")
    void unknownVehicle() {
        assertThrows(ResourceNotFoundException.class, () -> service().updateEquipment(TENANT, UUID.randomUUID(), new UpdateEquipmentRequest(bd("1"), bd("1"))));
    }

    @Test
    @DisplayName("the equipment list leaves out retired vehicles")
    void listLeavesOutRetired() {
        Vehicle live = tractor("LIVE 001 GP");
        Vehicle retired = tractor("OLD 001 GP");
        retired.changeStatusTo(VehicleStatus.RETIRED);
        when(vehicleRepository.findAllActive(eq(TENANT), any())).thenReturn(new PageImpl<>(List.of(live, retired)));

        List<EquipmentResponse> out = service().listEquipment(TENANT);

        assertEquals(List.of("LIVE 001 GP"), out.stream().map(EquipmentResponse::registration).toList());
    }
}
