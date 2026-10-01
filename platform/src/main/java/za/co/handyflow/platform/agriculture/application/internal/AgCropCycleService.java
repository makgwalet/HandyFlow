package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgCropType;
import za.co.handyflow.platform.agriculture.domain.model.AgEnterprise;
import za.co.handyflow.platform.agriculture.domain.model.AgProductionArea;
import za.co.handyflow.platform.agriculture.domain.model.AgSeason;
import za.co.handyflow.platform.agriculture.domain.model.AgInventoryItem;
import za.co.handyflow.platform.agriculture.domain.model.AgStockMovement;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropTypeRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgEnterpriseRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInventoryItemRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgProductionAreaRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgSeasonRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgSeasonLifecycle;
import za.co.handyflow.platform.agriculture.domain.repository.AgStockMovementRepository;
import za.co.handyflow.platform.agriculture.dto.CreateCropCycleRequest;
import za.co.handyflow.platform.agriculture.dto.CropCycleResponse;
import za.co.handyflow.platform.agriculture.dto.RecordPlantingRequest;
import za.co.handyflow.platform.agriculture.dto.UpdateCropCycleRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CRUD plus the planting/growing/harvest state-machine transitions for
 * {@link AgCropCycle} — the Crops sub-domain's central tracking unit,
 * playing the same role {@code AgAnimalService}/{@code AgGroupService}
 * play for Livestock.
 * <p>
 * {@link #recordPlanting} optionally issues a matching
 * {@link AgStockMovement} (ISSUE) against the referenced seed
 * {@link AgInventoryItem} in the same transaction, mirroring
 * {@code AgFeedRecordService.createFeedRecord()}'s own pattern exactly —
 * see that class for the precedent this method follows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgCropCycleService {

    private final AgCropCycleRepository cropCycleRepository;
    private final AgInventoryItemRepository inventoryItemRepository;
    private final AgStockMovementRepository stockMovementRepository;
    private final AgFarmRepository farmRepository;
    private final AgProductionAreaRepository productionAreaRepository;
    private final AgEnterpriseRepository enterpriseRepository;
    private final AgSeasonRepository seasonRepository;
    private final AgCropTypeRepository cropTypeRepository;

    @Transactional(readOnly = true)
    public Page<CropCycleResponse> getCropCyclesForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable) {
        Page<AgCropCycle> page = (status != null && !status.isBlank())
                ? cropCycleRepository.findByStatusForFarm(tenantId, farmId, status, pageable)
                : cropCycleRepository.findAllActiveForFarm(tenantId, farmId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CropCycleResponse> getCropCyclesForSeason(TenantId tenantId, UUID seasonId, Pageable pageable) {
        return cropCycleRepository.findAllActiveForSeason(tenantId, seasonId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CropCycleResponse getCropCycle(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public CropCycleResponse createCropCycle(TenantId tenantId, CreateCropCycleRequest req) {
        AgCropCycle cycle = AgCropCycle.create(tenantId, req.farmId(), req.productionAreaId(), req.enterpriseId(),
                req.seasonId(), req.cropTypeId(), req.variety(), req.cycleName(), req.areaPlantedHectares(),
                req.plantingDate(), req.expectedHarvestDate(), req.seedInventoryItemId(), req.seedQuantity(),
                req.seedSource(), req.notes());
        validateReferences(tenantId, cycle);
        cropCycleRepository.save(cycle);

        if (req.plantingDate() != null && req.seedInventoryItemId() != null) {
            issueSeed(tenantId, cycle, req.seedInventoryItemId(), req.seedQuantity(), req.seedSource());
        }

        log.info("Crop cycle created id={} farm={} tenant={}", cycle.getId(), req.farmId(), tenantId.getValue());
        return toResponse(cycle);
    }

    @Transactional
    public CropCycleResponse updateCropCycle(TenantId tenantId, UUID id, UpdateCropCycleRequest req) {
        AgCropCycle cycle = findActive(tenantId, id);
        cycle.update(req.variety(), req.cycleName(), req.areaPlantedHectares(), req.expectedHarvestDate(), req.notes());
        return toResponse(cycle);
    }

    @Transactional
    public CropCycleResponse recordPlanting(TenantId tenantId, UUID id, RecordPlantingRequest req) {
        AgCropCycle cycle = findActive(tenantId, id);
        cycle.recordPlanting(req.plantingDate(), req.seedInventoryItemId(), req.seedQuantity(), req.seedSource());

        if (req.seedInventoryItemId() != null) {
            issueSeed(tenantId, cycle, req.seedInventoryItemId(), req.seedQuantity(), req.seedSource());
        }

        log.info("Planting recorded cycle={} tenant={}", id, tenantId.getValue());
        return toResponse(cycle);
    }

    @Transactional
    public CropCycleResponse markGrowing(TenantId tenantId, UUID id) {
        AgCropCycle cycle = findActive(tenantId, id);
        cycle.markGrowing();
        return toResponse(cycle);
    }

    @Transactional
    public CropCycleResponse startHarvest(TenantId tenantId, UUID id) {
        AgCropCycle cycle = findActive(tenantId, id);
        cycle.startHarvest();
        return toResponse(cycle);
    }

    @Transactional
    public CropCycleResponse completeHarvest(TenantId tenantId, UUID id) {
        AgCropCycle cycle = findActive(tenantId, id);
        cycle.completeHarvest();
        return toResponse(cycle);
    }

    @Transactional
    public CropCycleResponse markFailed(TenantId tenantId, UUID id, String reason) {
        AgCropCycle cycle = findActive(tenantId, id);
        cycle.markFailed(reason);
        return toResponse(cycle);
    }

    @Transactional
    public CropCycleResponse abandon(TenantId tenantId, UUID id, String reason) {
        AgCropCycle cycle = findActive(tenantId, id);
        cycle.abandon(reason);
        return toResponse(cycle);
    }

    @Transactional
    public void deleteCropCycle(TenantId tenantId, UUID id) {
        AgCropCycle cycle = findActive(tenantId, id);
        cycle.softDelete();
        log.info("Crop cycle deleted id={} tenant={}", id, tenantId.getValue());
    }

    /**
     * Everything a new cycle points at must exist in this tenant and belong to the same farm. Without this a cycle could be
     * attached to another farm's field or season, or to an inactive crop type or closed season.
     */
    private void validateReferences(TenantId tenantId, AgCropCycle cycle) {
        UUID farmId = cycle.getFarmId();
        farmRepository.findActiveById(tenantId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
        AgProductionArea area = productionAreaRepository.findActiveById(tenantId, cycle.getProductionAreaId())
                .orElseThrow(() -> new ResourceNotFoundException("ProductionArea", cycle.getProductionAreaId().toString()));
        if (!farmId.equals(area.getFarmId())) {
            throw new IllegalArgumentException("production area " + area.getName() + " does not belong to this farm");
        }
        AgCropType cropType = cropTypeRepository.findActiveById(tenantId, cycle.getCropTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("CropType", cycle.getCropTypeId().toString()));
        if ("INACTIVE".equals(cropType.getStatus())) {
            throw new IllegalStateException("crop type " + cropType.getName() + " is inactive");
        }
        if (cycle.getEnterpriseId() != null) {
            AgEnterprise enterprise = enterpriseRepository.findActiveById(tenantId, cycle.getEnterpriseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Enterprise", cycle.getEnterpriseId().toString()));
            if (!farmId.equals(enterprise.getFarmId())) {
                throw new IllegalArgumentException("enterprise " + enterprise.getName() + " does not belong to this farm");
            }
        }
        if (cycle.getSeasonId() != null) {
            AgSeason season = seasonRepository.findActiveById(tenantId, cycle.getSeasonId())
                    .orElseThrow(() -> new ResourceNotFoundException("Season", cycle.getSeasonId().toString()));
            if (!farmId.equals(season.getFarmId())) {
                throw new IllegalArgumentException("season " + season.getName() + " does not belong to this farm");
            }
            AgSeasonLifecycle.requireOpenForNewCycles(season.getStatus());
        }
    }

    AgCropCycle findActive(TenantId tenantId, UUID id) {
        return cropCycleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("CropCycle", id.toString()));
    }

    private void issueSeed(TenantId tenantId, AgCropCycle cycle, UUID seedInventoryItemId,
                            BigDecimal seedQuantity, String seedSource) {
        if (seedQuantity == null || seedQuantity.signum() <= 0) return;
        AgInventoryItem item = inventoryItemRepository.findActiveById(tenantId, seedInventoryItemId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", seedInventoryItemId.toString()));
        if (!item.getFarmId().equals(cycle.getFarmId())) {
            throw new IllegalArgumentException("seed item " + item.getItemName() + " belongs to a different farm than this crop cycle");
        }
        item.issue(seedQuantity);
        // FIX (applied while building AgCostReportingService): this
        // previously passed unitCost=null, which meant seed cost was
        // unrecoverable anywhere — AgCropCycle itself has no seedCost field
        // (only seedQuantity), so this stock movement's own totalCost is
        // the ONLY place seed cost lives. Snapshotting item.getUnitCost()
        // here (same as AgFeedRecordService does with its caller-supplied
        // costPerKg) is what makes AgCostReportingService.summarizeCropCycle()
        // able to report a real seed-cost figure instead of silently zero.
        AgStockMovement movement = AgStockMovement.create(tenantId, seedInventoryItemId, "ISSUE",
                cycle.getPlantingDate(), seedQuantity, item.getUnitCost(), "AgCropCycle", cycle.getId(),
                null, null, "Seed issued for planting" + (seedSource != null ? " (" + seedSource + ")" : ""));
        stockMovementRepository.save(movement);
    }

    private CropCycleResponse toResponse(AgCropCycle c) {
        return new CropCycleResponse(
                c.getId(), c.getFarmId(), c.getProductionAreaId(), c.getEnterpriseId(), c.getSeasonId(),
                c.getCropTypeId(), c.getVariety(), c.getCycleName(), c.getAreaPlantedHectares(), c.getPlantingDate(),
                c.getExpectedHarvestDate(), c.getSeedInventoryItemId(), c.getSeedQuantity(), c.getSeedSource(),
                c.getStatus(), c.getNotes(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
