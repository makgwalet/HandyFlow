package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.AllocationShare;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostEntryResponse;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.CostEquipmentRequest;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.EquipmentOption;
import za.co.handyflow.platform.fleet.application.FleetFacade;
import za.co.handyflow.platform.fleet.application.FleetFacade.EquipmentSummary;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgEquipmentCostServiceTest {

    @Mock FleetFacade fleetFacade;
    @Mock AgFarmRepository farmRepository;
    @Mock AgCostEntryService costEntryService;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    final UUID farmId = UUID.randomUUID(), user = UUID.randomUUID(), vehicleId = UUID.randomUUID(), cycleId = UUID.randomUUID();
    static final LocalDate DAY = LocalDate.of(2026, 9, 10);

    private AgEquipmentCostService service() { return new AgEquipmentCostService(fleetFacade, farmRepository, costEntryService); }
    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private void farmExists() { when(farmRepository.findActiveById(eq(TENANT), eq(farmId))).thenReturn(Optional.of(mock(AgFarm.class))); }
    private void tractor(String rate) {
        EquipmentSummary s = new EquipmentSummary(vehicleId, "FARM 001 GP", "John Deere", "6120M", "TRACTOR", bd("1234.5"), rate == null ? null : bd(rate));
        when(fleetFacade.findEquipment(eq(TENANT), eq(vehicleId))).thenReturn(Optional.of(s));
    }
    private CostEquipmentRequest use(String hours) {
        return new CostEquipmentRequest(vehicleId, DAY, bd(hours), "Spraying", List.of(new AllocationShare("CROP_CYCLE", cycleId, bd("100"))));
    }
    private void verifyNothingRecorded() { verify(costEntryService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()); }

    @Test
    @DisplayName("the machines are listed with their rate, and a machine with no rate shows none")
    void lists() {
        EquipmentSummary a = new EquipmentSummary(vehicleId, "FARM 001 GP", "John Deere", "6120M", "TRACTOR", bd("1234.5"), bd("85.5"));
        EquipmentSummary b = new EquipmentSummary(UUID.randomUUID(), "FARM 002 GP", null, null, "TRUCK", null, null);
        when(fleetFacade.listEquipment(eq(TENANT))).thenReturn(List.of(a, b));

        List<EquipmentOption> out = service().equipment(TENANT);

        assertEquals("FARM 001 GP (John Deere 6120M)", out.get(0).description());
        assertEquals(0, bd("85.5").compareTo(out.get(0).operatingRatePerHour()));
        assertEquals("FARM 002 GP", out.get(1).description());
        assertNull(out.get(1).operatingRatePerHour());
    }

    @Test
    @DisplayName("a day's use is costed at Fleet's rate, snapshotted to four places, against the target, with the source pointing at the machine")
    void costsUse() {
        farmExists(); tractor("85.5");
        CostEntryResponse entry = mock(CostEntryResponse.class);
        when(costEntryService.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(entry));

        List<CostEntryResponse> out = service().costUse(TENANT, farmId, user, use("6"));

        assertEquals(1, out.size());
        verify(costEntryService).record(eq(TENANT), eq(farmId), eq(user), eq(DAY), eq("EQUIPMENT"), eq("Equipment: FARM 001 GP (John Deere 6120M), 6 h"), eq("FLEET_USAGE"),
                eq(vehicleId), eq(bd("6")), eq("h"), eq(bd("85.5000")), eq(bd("513.00")), eq("Spraying"), any());
    }

    @Test
    @DisplayName("a machine with no operating rate cannot be costed, and the message sends the user to Fleet")
    void refusesNoRate() {
        farmExists(); tractor(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service().costUse(TENANT, farmId, user, use("6")));

        assertTrue(ex.getMessage().contains("Fleet") && ex.getMessage().contains("FARM 001 GP"), ex.getMessage());
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("a rate of zero is not a rate")
    void refusesZeroRate() {
        farmExists(); tractor("0");
        assertThrows(IllegalArgumentException.class, () -> service().costUse(TENANT, farmId, user, use("6")));
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("hours must be above zero and at most a day")
    void refusesBadHours() {
        farmExists(); tractor("85.5");
        assertThrows(IllegalArgumentException.class, () -> service().costUse(TENANT, farmId, user, use("25")));
        assertThrows(IllegalArgumentException.class, () -> service().costUse(TENANT, farmId, user, use("0")));
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("a cost that rounds to nothing is refused")
    void refusesNothing() {
        farmExists(); tractor("0.01");
        assertThrows(IllegalArgumentException.class, () -> service().costUse(TENANT, farmId, user, use("0.1")));
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("an unknown machine or farm is a 404 and nothing is recorded")
    void unknowns() {
        farmExists();
        assertThrows(ResourceNotFoundException.class, () -> service().costUse(TENANT, farmId, user, use("6")));                  // the machine is not found
        assertThrows(ResourceNotFoundException.class, () -> service().costUse(TENANT, UUID.randomUUID(), user, use("6")));       // the farm is not found
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("a request with no targets is refused")
    void refusesNoTargets() {
        farmExists(); tractor("85.5");
        CostEquipmentRequest none = new CostEquipmentRequest(vehicleId, DAY, bd("6"), null, List.of());
        assertThrows(IllegalArgumentException.class, () -> service().costUse(TENANT, farmId, user, none));
    }
}
