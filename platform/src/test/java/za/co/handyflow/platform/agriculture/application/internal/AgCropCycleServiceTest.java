package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgCropType;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.model.AgProductionArea;
import za.co.handyflow.platform.agriculture.domain.model.AgSeason;
import za.co.handyflow.platform.agriculture.domain.repository.*;
import za.co.handyflow.platform.agriculture.dto.CreateCropCycleRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A new crop cycle may only point at things that exist in this tenant and belong to the same farm. Before these checks a
 * cycle could be attached to another farm's field or season, an inactive crop type or a closed season.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgCropCycleServiceTest {

    @Mock AgCropCycleRepository cropCycleRepository;
    @Mock AgInventoryItemRepository inventoryItemRepository;
    @Mock AgStockMovementRepository stockMovementRepository;
    @Mock AgFarmRepository farmRepository;
    @Mock AgProductionAreaRepository productionAreaRepository;
    @Mock AgEnterpriseRepository enterpriseRepository;
    @Mock AgSeasonRepository seasonRepository;
    @Mock AgCropTypeRepository cropTypeRepository;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    final UUID farmId = UUID.randomUUID();
    final UUID areaId = UUID.randomUUID();
    final UUID cropTypeId = UUID.randomUUID();
    final UUID seasonId = UUID.randomUUID();

    private AgCropCycleService service() {
        return new AgCropCycleService(cropCycleRepository, inventoryItemRepository, stockMovementRepository,
                farmRepository, productionAreaRepository, enterpriseRepository, seasonRepository, cropTypeRepository);
    }

    private CreateCropCycleRequest request(UUID season) {
        return new CreateCropCycleRequest(farmId, areaId, null, season, cropTypeId, null, "Field 3 maize",
                new BigDecimal("10"), null, null, null, null, null, null);
    }

    private void validReferences(UUID areaFarmId, String cropTypeStatus) {
        when(farmRepository.findActiveById(eq(TENANT), eq(farmId))).thenReturn(Optional.of(mock(AgFarm.class)));
        AgProductionArea area = mock(AgProductionArea.class);
        when(area.getFarmId()).thenReturn(areaFarmId);
        when(area.getName()).thenReturn("Field 3");
        when(productionAreaRepository.findActiveById(eq(TENANT), eq(areaId))).thenReturn(Optional.of(area));
        AgCropType type = mock(AgCropType.class);
        when(type.getStatus()).thenReturn(cropTypeStatus);
        when(type.getName()).thenReturn("Maize");
        when(cropTypeRepository.findActiveById(eq(TENANT), eq(cropTypeId))).thenReturn(Optional.of(type));
    }

    private void season(UUID seasonFarmId, String status) {
        AgSeason season = mock(AgSeason.class);
        when(season.getFarmId()).thenReturn(seasonFarmId);
        when(season.getStatus()).thenReturn(status);
        when(season.getName()).thenReturn("2026/27 summer");
        when(seasonRepository.findActiveById(eq(TENANT), eq(seasonId))).thenReturn(Optional.of(season));
    }

    @Test
    @DisplayName("creates the cycle when every reference exists and belongs to the farm")
    void createsWhenReferencesAreValid() {
        validReferences(farmId, "ACTIVE");
        season(farmId, "ACTIVE");

        service().createCropCycle(TENANT, request(seasonId));

        verify(cropCycleRepository).save(any(AgCropCycle.class));
    }

    @Test
    @DisplayName("refuses a production area that belongs to another farm")
    void refusesAreaOfAnotherFarm() {
        validReferences(UUID.randomUUID(), "ACTIVE");

        assertThrows(IllegalArgumentException.class, () -> service().createCropCycle(TENANT, request(null)));
        verify(cropCycleRepository, never()).save(any(AgCropCycle.class));
    }

    @Test
    @DisplayName("refuses an inactive crop type")
    void refusesInactiveCropType() {
        validReferences(farmId, "INACTIVE");

        assertThrows(IllegalStateException.class, () -> service().createCropCycle(TENANT, request(null)));
        verify(cropCycleRepository, never()).save(any(AgCropCycle.class));
    }

    @Test
    @DisplayName("refuses a closed season")
    void refusesClosedSeason() {
        validReferences(farmId, "ACTIVE");
        season(farmId, "CLOSED");

        assertThrows(IllegalStateException.class, () -> service().createCropCycle(TENANT, request(seasonId)));
        verify(cropCycleRepository, never()).save(any(AgCropCycle.class));
    }

    @Test
    @DisplayName("refuses a season that belongs to another farm")
    void refusesSeasonOfAnotherFarm() {
        validReferences(farmId, "ACTIVE");
        season(UUID.randomUUID(), "ACTIVE");

        assertThrows(IllegalArgumentException.class, () -> service().createCropCycle(TENANT, request(seasonId)));
        verify(cropCycleRepository, never()).save(any(AgCropCycle.class));
    }

    @Test
    @DisplayName("refuses a farm that does not exist in this tenant")
    void refusesUnknownFarm() {
        assertThrows(ResourceNotFoundException.class, () -> service().createCropCycle(TENANT, request(null)));
        verify(cropCycleRepository, never()).save(any(AgCropCycle.class));
    }
}
