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
import za.co.handyflow.platform.agriculture.domain.model.AgHarvestRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropTypeRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgHarvestRecordRepository;
import za.co.handyflow.platform.agriculture.dto.CreateHarvestRecordRequest;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgHarvestRecordServiceTest {

    @Mock AgHarvestRecordRepository harvestRecordRepository;
    @Mock AgCropCycleRepository cropCycleRepository;
    @Mock AgCropTypeRepository cropTypeRepository;
    @Mock HrFacade hrFacade;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    final UUID cropTypeId = UUID.randomUUID();
    final UUID cycleId = UUID.randomUUID();

    private AgHarvestRecordService service() {
        return new AgHarvestRecordService(harvestRecordRepository, cropCycleRepository, cropTypeRepository, hrFacade);
    }

    private void cycle(LocalDate plantingDate) {
        AgCropCycle cycle = AgCropCycle.create(TENANT, UUID.randomUUID(), UUID.randomUUID(), null, null, cropTypeId,
                null, null, new BigDecimal("10"), plantingDate, null, null, null, null, null);
        when(cropCycleRepository.findActiveById(eq(TENANT), eq(cycleId))).thenReturn(Optional.of(cycle));
    }

    private void cropUnit(String unit) {
        AgCropType type = mock(AgCropType.class);
        when(type.getDefaultUnitOfMeasure()).thenReturn(unit);
        when(cropTypeRepository.findActiveById(eq(TENANT), eq(cropTypeId))).thenReturn(Optional.of(type));
    }

    private CreateHarvestRecordRequest request(String unit) {
        return new CreateHarvestRecordRequest(LocalDate.now(), new BigDecimal("12.5"), unit, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("accepts kilograms for a crop reported in tonnes (they convert)")
    void acceptsConvertibleUnit() {
        cycle(LocalDate.now());
        cropUnit("t");

        service().createHarvestRecord(TENANT, cycleId, request("kg"));

        verify(harvestRecordRepository).save(any(AgHarvestRecord.class));
    }

    @Test
    @DisplayName("refuses a unit that cannot be converted to the crop's unit")
    void refusesIncompatibleUnit() {
        cycle(LocalDate.now());
        cropUnit("t");

        assertThrows(IllegalArgumentException.class, () -> service().createHarvestRecord(TENANT, cycleId, request("bags")));
        verify(harvestRecordRepository, never()).save(any(AgHarvestRecord.class));
    }

    @Test
    @DisplayName("refuses a harvest on a cycle that has not been planted")
    void refusesUnplantedCycle() {
        cycle(null);
        cropUnit("t");

        assertThrows(IllegalStateException.class, () -> service().createHarvestRecord(TENANT, cycleId, request("t")));
        verify(harvestRecordRepository, never()).save(any(AgHarvestRecord.class));
    }

    @Test
    @DisplayName("skips the unit check when the crop type no longer exists")
    void skipsCheckWithoutCropType() {
        cycle(LocalDate.now());

        service().createHarvestRecord(TENANT, cycleId, request("bags"));

        verify(harvestRecordRepository).save(any(AgHarvestRecord.class));
    }
}
