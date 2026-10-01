package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgCostEntry;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCostEntryRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgEnterpriseRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgCostAllocation;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.AllocationShare;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CategoryTotal;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostEntryResponse;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostTotalsResponse;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CreateCostEntryRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Agriculture cost ledger (ADR-001, W1). Allocates a cost across production targets and keeps it append-only: a correction is
 * a reversal, never an edit. Only OTHER_DIRECT can be entered by hand; labour, equipment and fuel are costed from HR, Fleet and
 * Fuel by {@link #record} when those integrations arrive, so they are never typed in twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgCostEntryService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final List<String> CATEGORY_ORDER = List.of("LABOUR", "EQUIPMENT", "FUEL", "OTHER_DIRECT");

    private final AgCostEntryRepository costEntryRepository;
    private final AgFarmRepository farmRepository;
    private final AgCropCycleRepository cropCycleRepository;
    private final AgGroupRepository groupRepository;
    private final AgAnimalRepository animalRepository;
    private final AgEnterpriseRepository enterpriseRepository;

    @Transactional
    public List<CostEntryResponse> createManual(TenantId tenantId, UUID farmId, UUID userId, CreateCostEntryRequest req) {
        String category = req.category() == null || req.category().isBlank() ? "OTHER_DIRECT" : req.category();
        if (!"OTHER_DIRECT".equals(category)) {
            throw new IllegalArgumentException("only OTHER_DIRECT costs can be entered by hand; labour, equipment and fuel are costed from HR, Fleet and Fuel");
        }
        List<AgCostAllocation.Share> shares = new ArrayList<>();
        for (AllocationShare a : req.allocations()) shares.add(new AgCostAllocation.Share(a.targetType(), a.targetId(), a.percentage()));
        return record(tenantId, farmId, userId, req.entryDate(), category, req.description(), "MANUAL", null,
                req.quantity(), req.unit(), null, req.amount(), req.notes(), shares);
    }

    /**
     * Writes one cost, split across its targets, as one allocation group. The entry point later integrations use too, with their own
     * {@code sourceType}/{@code sourceRef} and the rate they snapshotted.
     */
    @Transactional
    public List<CostEntryResponse> record(TenantId tenantId, UUID farmId, UUID userId, LocalDate entryDate, String category,
                                          String description, String sourceType, UUID sourceRef, BigDecimal quantity, String unit,
                                          BigDecimal rate, BigDecimal amount, String notes, List<AgCostAllocation.Share> shares) {
        farmRepository.findActiveById(tenantId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
        if (entryDate != null && entryDate.isAfter(LocalDate.now(SAST))) {
            throw new IllegalArgumentException("entryDate cannot be in the future");
        }
        List<AgCostAllocation.Part> parts = AgCostAllocation.split(amount, shares);
        for (AgCostAllocation.Part p : parts) requireTargetOnFarm(tenantId, farmId, p.targetType(), p.targetId());

        UUID groupId = UUID.randomUUID();
        List<AgCostEntry> entries = new ArrayList<>();
        for (AgCostAllocation.Part p : parts) {
            entries.add(AgCostEntry.create(tenantId, farmId, entryDate, category, description, sourceType, sourceRef,
                    p.targetType(), p.targetId(), quantity, unit, rate, p.amount(), p.percentage(), groupId, notes, userId));
        }
        costEntryRepository.saveAll(entries);
        log.info("Cost recorded group={} category={} amount={} targets={} tenant={}", groupId, category, amount, parts.size(), tenantId.getValue());
        return entries.stream().map(this::toResponse).toList();
    }

    /** Reverses every row of an allocation group (a split cost is reversed as a whole). Returns the reversal rows. */
    @Transactional
    public List<CostEntryResponse> reverseGroup(TenantId tenantId, UUID allocationGroupId, UUID userId, String reason) {
        List<AgCostEntry> rows = costEntryRepository.findByAllocationGroup(tenantId, allocationGroupId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("CostEntryGroup", allocationGroupId.toString());
        if (rows.stream().anyMatch(r -> !AgCostEntry.ACTIVE.equals(r.getStatus()))) {
            throw new IllegalStateException("this cost has already been reversed");
        }
        List<AgCostEntry> reversals = new ArrayList<>();
        for (AgCostEntry r : rows) reversals.add(r.reverse(reason, userId));
        costEntryRepository.saveAll(rows);
        costEntryRepository.saveAll(reversals);
        log.info("Cost reversed group={} rows={} tenant={}", allocationGroupId, rows.size(), tenantId.getValue());
        return reversals.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<CostEntryResponse> list(TenantId tenantId, UUID farmId, String targetType, UUID targetId, Pageable pageable) {
        requireFarm(tenantId, farmId);
        if (targetType != null) {
            requireValidTarget(targetType, targetId);
            return costEntryRepository.findForTarget(tenantId, targetType, targetId, pageable).map(this::toResponse);
        }
        return costEntryRepository.findForFarm(tenantId, farmId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CostTotalsResponse totals(TenantId tenantId, UUID farmId, String targetType, UUID targetId) {
        requireFarm(tenantId, farmId);
        List<Object[]> rows;
        if (targetType != null) {
            requireValidTarget(targetType, targetId);
            rows = costEntryRepository.sumByCategoryForTarget(tenantId, targetType, targetId);
        } else {
            rows = costEntryRepository.sumByCategoryForFarm(tenantId, farmId);
        }
        Map<String, BigDecimal> byCategory = new HashMap<>();
        for (Object[] r : rows) byCategory.put((String) r[0], r[1] instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(r[1])));
        List<CategoryTotal> totals = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (String c : CATEGORY_ORDER) {
            BigDecimal v = byCategory.get(c);
            if (v == null) continue;
            totals.add(new CategoryTotal(c, v));
            total = total.add(v);
        }
        return new CostTotalsResponse(totals, total);
    }

    private void requireFarm(TenantId tenantId, UUID farmId) {
        farmRepository.findActiveById(tenantId, farmId).orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
    }

    private static void requireValidTarget(String targetType, UUID targetId) {
        if (!AgCostAllocation.TARGET_TYPES.contains(targetType)) {
            throw new IllegalArgumentException("targetType must be one of " + AgCostAllocation.TARGET_TYPES);
        }
        if (targetId == null) throw new IllegalArgumentException("targetId is required with targetType");
    }

    /** The target must exist in this tenant AND belong to this farm, so a cost can never be pinned on another farm's crop or herd. */
    private void requireTargetOnFarm(TenantId tenantId, UUID farmId, String targetType, UUID targetId) {
        UUID owner = switch (targetType) {
            case AgCostAllocation.CROP_CYCLE -> cropCycleRepository.findActiveById(tenantId, targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("CropCycle", targetId.toString())).getFarmId();
            case AgCostAllocation.GROUP -> groupRepository.findActiveById(tenantId, targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Group", targetId.toString())).getFarmId();
            case AgCostAllocation.ANIMAL -> animalRepository.findActiveById(tenantId, targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Animal", targetId.toString())).getFarmId();
            case AgCostAllocation.ENTERPRISE -> enterpriseRepository.findActiveById(tenantId, targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Enterprise", targetId.toString())).getFarmId();
            default -> throw new IllegalArgumentException("targetType must be one of " + AgCostAllocation.TARGET_TYPES);
        };
        if (!farmId.equals(owner)) {
            throw new IllegalArgumentException(targetType.toLowerCase().replace('_', ' ') + " " + targetId + " does not belong to this farm");
        }
    }

    private CostEntryResponse toResponse(AgCostEntry e) {
        return new CostEntryResponse(e.getId(), e.getFarmId(), e.getEntryDate(), e.getCategory(), e.getDescription(),
                e.getSourceType(), e.getSourceRef(), e.getTargetType(), e.getTargetId(), e.getQuantity(), e.getUnit(), e.getRate(),
                e.getAmount(), e.getPercentage(), e.getAllocationGroupId(), e.getReversesEntryId(), e.getStatus(), e.getNotes(),
                e.getCreatedBy(), e.getCreatedAt());
    }
}
