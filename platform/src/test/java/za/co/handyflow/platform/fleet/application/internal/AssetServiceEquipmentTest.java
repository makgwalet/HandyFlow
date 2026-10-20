package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.fleet.domain.model.AssetClass;
import za.co.handyflow.platform.fleet.domain.model.AssetProfile;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.repository.AssetAttachmentRepository;
import za.co.handyflow.platform.fleet.domain.repository.AssetMeterReadingRepository;
import za.co.handyflow.platform.fleet.domain.repository.AssetProfileRepository;
import za.co.handyflow.platform.fleet.domain.repository.FuelFillupRepository;
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
class AssetServiceEquipmentTest {

    @Mock VehicleRepository vehicleRepository;
    @Mock AssetProfileRepository profileRepository;
    static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private AssetService service() {
        return new AssetService(vehicleRepository, profileRepository, mock(AssetMeterReadingRepository.class),
                mock(AssetAttachmentRepository.class), mock(VehicleServiceRepository.class), mock(FuelFillupRepository.class),
                mock(NotificationService.class), mock(TenantAdminRecipients.class));
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private static Vehicle tractor(String registration) {
        return Vehicle.create(TENANT, registration, "John Deere", "6120M", 2021, "Green", null, "TRACTOR", "DIESEL", null, null, null, null, bd("200"), 10000, null, null, null);
    }

    @Test
    @DisplayName("a new rate creates the asset profile and the meter moves up")
    void updatesAndCreatesTheProfile() {
        Vehicle v = tractor("FARM 001 GP");
        UUID id = v.getId();
        when(vehicleRepository.findActiveById(eq(TENANT), eq(id))).thenReturn(Optional.of(v));
        when(profileRepository.findByTenantIdAndVehicleId(TENANT, id)).thenReturn(Optional.empty());

        EquipmentResponse r = service().updateEquipment(TENANT, id, new UpdateEquipmentRequest(bd("1234.5"), bd("85.5")));

        assertEquals(0, bd("1234.5").compareTo(r.engineHours()));
        assertEquals(0, bd("85.5").compareTo(r.operatingRatePerHour()));
        assertEquals("FARM 001 GP", r.registration());
        assertEquals(0, bd("1234.5").compareTo(v.getEngineHours()));
        verify(vehicleRepository).save(eq(v));
        verify(profileRepository).save(any(AssetProfile.class));
    }

    @Test
    @DisplayName("an existing profile keeps its class and only the rate changes; null clears it")
    void changesTheRateOnAnExistingProfile() {
        Vehicle v = tractor("FARM 001 GP");
        UUID id = v.getId();
        AssetProfile p = AssetProfile.create(TENANT, id);
        p.apply(AssetClass.TRACTOR, bd("50"), bd("8"), null, null, null, null, null, null);
        when(vehicleRepository.findActiveById(eq(TENANT), eq(id))).thenReturn(Optional.of(v));
        when(profileRepository.findByTenantIdAndVehicleId(TENANT, id)).thenReturn(Optional.of(p));

        EquipmentResponse r = service().updateEquipment(TENANT, id, new UpdateEquipmentRequest(null, bd("90")));
        assertEquals(0, bd("90").compareTo(r.operatingRatePerHour()));
        assertEquals(AssetClass.TRACTOR, p.getAssetClass());
        verify(vehicleRepository, never()).save(any());

        assertNull(service().updateEquipment(TENANT, id, new UpdateEquipmentRequest(null, null)).operatingRatePerHour());
    }

    @Test
    @DisplayName("a negative number, or a meter reading lower than the current one, is refused and nothing is saved")
    void refusesBadNumbers() {
        Vehicle v = tractor("FARM 001 GP");
        UUID id = v.getId();
        v.updateEngineHours(bd("500"));
        when(vehicleRepository.findActiveById(eq(TENANT), eq(id))).thenReturn(Optional.of(v));

        assertThrows(IllegalArgumentException.class, () -> service().updateEquipment(TENANT, id, new UpdateEquipmentRequest(bd("100"), bd("-1"))));
        assertThrows(IllegalArgumentException.class, () -> service().updateEquipment(TENANT, id, new UpdateEquipmentRequest(bd("-5"), null)));
        assertThrows(IllegalArgumentException.class, () -> service().updateEquipment(TENANT, id, new UpdateEquipmentRequest(bd("100"), null)));

        assertEquals(0, bd("500").compareTo(v.getEngineHours()));
        verify(vehicleRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("an unknown or deleted vehicle is a 404")
    void unknownVehicle() {
        assertThrows(ResourceNotFoundException.class, () -> service().updateEquipment(TENANT, UUID.randomUUID(), new UpdateEquipmentRequest(bd("1"), bd("1"))));
    }

    @Test
    @DisplayName("the equipment list leaves out retired vehicles, is sorted by registration and carries each rate")
    void listLeavesOutRetired() {
        Vehicle b = tractor("b-harvester");
        Vehicle a = tractor("A-TRACTOR");
        Vehicle retired = tractor("OLD 001 GP");
        retired.changeStatusTo(VehicleStatus.RETIRED);
        AssetProfile pa = AssetProfile.create(TENANT, a.getId());
        pa.apply(AssetClass.TRACTOR, bd("85"), bd("8"), null, null, null, null, null, null);
        when(vehicleRepository.findAllActive(eq(TENANT), any())).thenReturn(new PageImpl<>(List.of(b, retired, a)));
        when(profileRepository.findByTenantIdAndVehicleIdIn(eq(TENANT), any())).thenReturn(List.of(pa));

        List<EquipmentResponse> out = service().listEquipment(TENANT);

        assertEquals(List.of("A-TRACTOR", "b-harvester"), out.stream().map(EquipmentResponse::registration).toList());
        assertEquals(0, bd("85").compareTo(out.get(0).operatingRatePerHour()));
        assertNull(out.get(1).operatingRatePerHour());
    }
}
