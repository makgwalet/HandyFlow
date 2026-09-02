package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgCropType;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.repository.*;
import za.co.handyflow.platform.agriculture.dto.AnimalCostSummaryResponse;
import za.co.handyflow.platform.agriculture.dto.CropCycleCostSummaryResponse;
import za.co.handyflow.platform.agriculture.dto.GroupCostSummaryResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for AgCostReportingService — no Spring context, mocked
 * repositories, matching ClinicServiceTest/ClinicBillingServiceTest's own
 * established Mockito convention for this codebase's service-level tests.
 * <p>
 * Focus is the null-safe division behavior (the whole reason
 * FleetCostService's own pattern returns null rather than zero on a
 * zero denominator) and that totals sum the right fields — not
 * re-testing entity invariants already covered by AgGroupTest/
 * AgCropCycleTest etc.
 */
@ExtendWith(MockitoExtension.class)
class AgCostReportingServiceTest {

    @Mock AgAnimalRepository animalRepository;
    @Mock AgHealthEventRepository healthEventRepository;
    @Mock AgFeedRecordRepository feedRecordRepository;
    @Mock AgGroupRepository groupRepository;
    @Mock AgCropCycleRepository cropCycleRepository;
    @Mock AgCropTypeRepository cropTypeRepository;
    @Mock AgInputApplicationRepository inputApplicationRepository;
    @Mock AgStockMovementRepository stockMovementRepository;
    @Mock AgHarvestRecordRepository harvestRecordRepository;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private AgCostReportingService newService() {
        return new AgCostReportingService(animalRepository, healthEventRepository, feedRecordRepository,
                groupRepository, cropCycleRepository, cropTypeRepository, inputApplicationRepository,
                stockMovementRepository, harvestRecordRepository);
    }

    @Nested
    @DisplayName("getAnimalCostSummary")
    class AnimalCost {

        @Test
        @DisplayName("sums acquisition + health + feed cost, and computes cost-per-kg when weight is known")
        void computesTotalAndCostPerKg() {
            AgAnimal animal = AgAnimal.create(TENANT, UUID.randomUUID(), null, null, UUID.randomUUID(),
                    "TAG-001", null, "Bonsmara", "FEMALE", null, false, null, null,
                    "PURCHASED", LocalDate.now(), new BigDecimal("8000.00"));
            animal.recordWeight(new BigDecimal("400.00"));

            when(animalRepository.findActiveById(eq(TENANT), any())).thenReturn(Optional.of(animal));
            when(healthEventRepository.sumCostByAnimal(eq(TENANT), any())).thenReturn(new BigDecimal("350.00"));
            when(feedRecordRepository.sumTotalCostByAnimal(eq(TENANT), any())).thenReturn(new BigDecimal("1250.00"));

            AnimalCostSummaryResponse result = newService().getAnimalCostSummary(TENANT, UUID.randomUUID());

            assertThat(result.acquisitionCost()).isEqualByComparingTo("8000.00");
            assertThat(result.totalHealthCost()).isEqualByComparingTo("350.00");
            assertThat(result.totalFeedCost()).isEqualByComparingTo("1250.00");
            assertThat(result.totalCost()).isEqualByComparingTo("9600.00");
            // 9600.00 / 400.00 = 24.00
            assertThat(result.costPerKgLiveweight()).isEqualByComparingTo("24.00");
        }

        @Test
        @DisplayName("returns null cost-per-kg, not zero, when no weight has been recorded")
        void nullCostPerKgWhenNoWeight() {
            AgAnimal animal = AgAnimal.create(TENANT, UUID.randomUUID(), null, null, UUID.randomUUID(),
                    "TAG-002", null, null, "MALE", null, false, null, null,
                    "BORN_ON_FARM", LocalDate.now(), null);

            when(animalRepository.findActiveById(eq(TENANT), any())).thenReturn(Optional.of(animal));
            when(healthEventRepository.sumCostByAnimal(eq(TENANT), any())).thenReturn(BigDecimal.ZERO);
            when(feedRecordRepository.sumTotalCostByAnimal(eq(TENANT), any())).thenReturn(BigDecimal.ZERO);

            AnimalCostSummaryResponse result = newService().getAnimalCostSummary(TENANT, UUID.randomUUID());

            assertThat(result.currentWeightKg()).isNull();
            assertThat(result.costPerKgLiveweight()).isNull();
            assertThat(result.acquisitionCost()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("getGroupCostSummary")
    class GroupCost {

        @Test
        @DisplayName("excludes acquisition cost and computes cost-per-head")
        void excludesAcquisitionComputesCostPerHead() {
            AgGroup group = AgGroup.create(TENANT, UUID.randomUUID(), null, null, UUID.randomUUID(),
                    "BATCH-01", "Ross 308", 100, LocalDate.now(), "PURCHASED");

            when(groupRepository.findActiveById(eq(TENANT), any())).thenReturn(Optional.of(group));
            when(healthEventRepository.sumCostByGroup(eq(TENANT), any())).thenReturn(new BigDecimal("200.00"));
            when(feedRecordRepository.sumTotalCostByGroup(eq(TENANT), any())).thenReturn(new BigDecimal("800.00"));

            GroupCostSummaryResponse result = newService().getGroupCostSummary(TENANT, UUID.randomUUID());

            // 200 + 800 = 1000, no acquisition cost field exists on AgGroup at all
            assertThat(result.totalCost()).isEqualByComparingTo("1000.00");
            // 1000 / 100 head = 10.00 per head
            assertThat(result.costPerHead()).isEqualByComparingTo("10.00");
            assertThat(result.currentCount()).isEqualTo(100);
        }

        @Test
        @DisplayName("returns null cost-per-head, not zero, once the group is fully closed out at zero count")
        void nullCostPerHeadWhenCountIsZero() {
            AgGroup group = AgGroup.create(TENANT, UUID.randomUUID(), null, null, UUID.randomUUID(),
                    "BATCH-02", null, 10, LocalDate.now(), "PURCHASED");
            group.reduceCount(10); // currentCount -> 0, status -> CLOSED

            when(groupRepository.findActiveById(eq(TENANT), any())).thenReturn(Optional.of(group));
            when(healthEventRepository.sumCostByGroup(eq(TENANT), any())).thenReturn(new BigDecimal("50.00"));
            when(feedRecordRepository.sumTotalCostByGroup(eq(TENANT), any())).thenReturn(BigDecimal.ZERO);

            GroupCostSummaryResponse result = newService().getGroupCostSummary(TENANT, UUID.randomUUID());

            assertThat(result.currentCount()).isZero();
            assertThat(result.costPerHead()).isNull();
        }
    }

    @Nested
    @DisplayName("getCropCycleCostSummary")
    class CropCycleCost {

        @Test
        @DisplayName("sums seed + input cost into cost-per-hectare, and computes yield-per-hectare")
        void computesCostAndYieldPerHectare() {
            UUID cropTypeId = UUID.randomUUID();
            AgCropCycle cycle = AgCropCycle.create(TENANT, UUID.randomUUID(), UUID.randomUUID(), null, null,
                    cropTypeId, "Yellow", "North Field Maize 2026", new BigDecimal("10.00"),
                    LocalDate.now(), null, null, null, null, null);
            AgCropType cropType = AgCropType.create(TENANT, "Maize", "FIELD_CROP", 120, "kg");

            when(cropCycleRepository.findActiveById(eq(TENANT), any())).thenReturn(Optional.of(cycle));
            when(stockMovementRepository.sumTotalCostByReference(eq(TENANT), eq("AgCropCycle"), any()))
                    .thenReturn(new BigDecimal("500.00")); // seed cost
            when(inputApplicationRepository.sumCostByCropCycle(eq(TENANT), any())).thenReturn(new BigDecimal("1500.00"));
            when(inputApplicationRepository.sumLaborHoursByCropCycle(eq(TENANT), any())).thenReturn(new BigDecimal("40.00"));
            when(harvestRecordRepository.sumQuantityByCropCycle(eq(TENANT), any())).thenReturn(new BigDecimal("6000.000"));
            when(cropTypeRepository.findActiveById(eq(TENANT), eq(cropTypeId))).thenReturn(Optional.of(cropType));

            CropCycleCostSummaryResponse result = newService().getCropCycleCostSummary(TENANT, UUID.randomUUID());

            // 500 + 1500 = 2000 total cost over 10 hectares = 200.00/ha
            assertThat(result.totalCost()).isEqualByComparingTo("2000.00");
            assertThat(result.costPerHectare()).isEqualByComparingTo("200.00");
            // 6000 kg / 10 ha = 600.000 kg/ha
            assertThat(result.yieldPerHectare()).isEqualByComparingTo("600.000");
            assertThat(result.yieldUnitOfMeasure()).isEqualTo("kg");
            assertThat(result.totalLaborHours()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("falls back to a null yield unit when the crop type can't be resolved, without failing the whole report")
        void nullYieldUnitWhenCropTypeMissing() {
            UUID cropTypeId = UUID.randomUUID();
            AgCropCycle cycle = AgCropCycle.create(TENANT, UUID.randomUUID(), UUID.randomUUID(), null, null,
                    cropTypeId, null, null, new BigDecimal("5.00"),
                    null, null, null, null, null, null);

            when(cropCycleRepository.findActiveById(eq(TENANT), any())).thenReturn(Optional.of(cycle));
            when(stockMovementRepository.sumTotalCostByReference(eq(TENANT), eq("AgCropCycle"), any())).thenReturn(BigDecimal.ZERO);
            when(inputApplicationRepository.sumCostByCropCycle(eq(TENANT), any())).thenReturn(BigDecimal.ZERO);
            when(inputApplicationRepository.sumLaborHoursByCropCycle(eq(TENANT), any())).thenReturn(BigDecimal.ZERO);
            when(harvestRecordRepository.sumQuantityByCropCycle(eq(TENANT), any())).thenReturn(BigDecimal.ZERO);
            when(cropTypeRepository.findActiveById(eq(TENANT), eq(cropTypeId))).thenReturn(Optional.empty());

            CropCycleCostSummaryResponse result = newService().getCropCycleCostSummary(TENANT, UUID.randomUUID());

            assertThat(result.yieldUnitOfMeasure()).isNull();
            assertThat(result.totalCost()).isEqualByComparingTo(BigDecimal.ZERO);
            // still not null — an actively-tracked cycle always has a positive area (entity-enforced)
            assertThat(result.costPerHectare()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
