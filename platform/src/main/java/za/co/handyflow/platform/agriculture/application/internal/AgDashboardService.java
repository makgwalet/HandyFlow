package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgHealthEvent;
import za.co.handyflow.platform.agriculture.domain.model.AgInventoryItem;
import za.co.handyflow.platform.agriculture.domain.model.AgScoutingRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgHealthEventRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInventoryItemRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgScoutingRecordRepository;
import za.co.handyflow.platform.agriculture.dto.AttentionItemResponse;
import za.co.handyflow.platform.agriculture.dto.FarmTodaySummaryResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * FIX (Agriculture GAP 4 & GAP 5 — mobile gap report): a new, dedicated
 * service rather than adding six more repository dependencies to
 * AgFarmService, which is otherwise narrowly scoped (farmRepository +
 * hrFacade only). The two endpoints this backs share the same attention-
 * list computation, which is why they live together here — GAP 4's
 * "Home/Today" summary embeds the same list GAP 5's own dedicated
 * "attention" endpoint returns on its own, for screens that only need
 * the alerts, not the counts too.
 * <p>
 * Deliberately owns farmRepository purely to confirm the farm exists and
 * belongs to this tenant before running the rest of the queries, same
 * validate-then-proceed shape used everywhere else in this module.
 */
@Service
@RequiredArgsConstructor
public class AgDashboardService {

    private final AgFarmRepository farmRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final AgCropCycleRepository cropCycleRepository;
    private final AgHealthEventRepository healthEventRepository;
    private final AgScoutingRecordRepository scoutingRecordRepository;
    private final AgInventoryItemRepository inventoryItemRepository;

    @Transactional(readOnly = true)
    public FarmTodaySummaryResponse getTodaySummary(TenantId tenantId, UUID farmId) {
        requireFarm(tenantId, farmId);

        long animalCount = animalRepository.countActiveForFarm(tenantId, farmId);
        long groupCount = groupRepository.countActiveForFarm(tenantId, farmId);
        long cropCycleCount = cropCycleRepository.countActiveForFarm(tenantId, farmId);
        List<AttentionItemResponse> attention = buildAttentionList(tenantId, farmId);

        return new FarmTodaySummaryResponse(farmId, animalCount, groupCount, cropCycleCount, attention);
    }

    @Transactional(readOnly = true)
    public List<AttentionItemResponse> getAttention(TenantId tenantId, UUID farmId) {
        requireFarm(tenantId, farmId);
        return buildAttentionList(tenantId, farmId);
    }

    private void requireFarm(TenantId tenantId, UUID farmId) {
        farmRepository.findActiveById(tenantId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
    }

    /**
     * Ranked OVERDUE first, then DUE_TODAY, then MEDIUM (low-stock items,
     * which have no due date to sort by) — matching the gap report's own
     * stated want ("ranked by severity"). Within a severity tier, sorted
     * by due date ascending (most overdue first) where one exists.
     */
    private List<AttentionItemResponse> buildAttentionList(TenantId tenantId, UUID farmId) {
        LocalDate today = LocalDate.now();
        List<AttentionItemResponse> items = new ArrayList<>();

        for (AgHealthEvent e : healthEventRepository.findDueForFarm(tenantId, farmId, today)) {
            items.add(new AttentionItemResponse(
                    "HEALTH_EVENT_DUE",
                    e.getNextDueDate().isBefore(today) ? "OVERDUE" : "DUE_TODAY",
                    e.getEventType() + " due",
                    e.getDescription(),
                    e.getNextDueDate(),
                    e.getId()));
        }

        for (AgScoutingRecord r : scoutingRecordRepository.findFollowUpDueForFarm(tenantId, farmId, today)) {
            items.add(new AttentionItemResponse(
                    "SCOUTING_FOLLOWUP_DUE",
                    r.getFollowUpDate().isBefore(today) ? "OVERDUE" : "DUE_TODAY",
                    "Scouting follow-up due",
                    r.getRecommendedAction() != null ? r.getRecommendedAction() : r.getDescription(),
                    r.getFollowUpDate(),
                    r.getId()));
        }

        for (AgInventoryItem i : inventoryItemRepository.findBelowReorderLevelForFarm(tenantId, farmId)) {
            items.add(new AttentionItemResponse(
                    "LOW_STOCK",
                    "MEDIUM",
                    i.getItemName() + " is low",
                    String.format("%s %s remaining (reorder level: %s %s)",
                            i.getCurrentQuantity(), i.getUnitOfMeasure(), i.getReorderLevel(), i.getUnitOfMeasure()),
                    null,
                    i.getId()));
        }

        Comparator<AttentionItemResponse> bySeverity = Comparator.comparing(
                a -> switch (a.severity()) { case "OVERDUE" -> 0; case "DUE_TODAY" -> 1; default -> 2; });
        items.sort(bySeverity.thenComparing(
                a -> a.dueDate() != null ? a.dueDate() : LocalDate.MAX));
        return items;
    }
}
