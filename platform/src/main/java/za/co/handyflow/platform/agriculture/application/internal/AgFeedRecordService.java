package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgFeedRecord;
import za.co.handyflow.platform.agriculture.domain.model.AgInventoryItem;
import za.co.handyflow.platform.agriculture.domain.model.AgStockMovement;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFeedRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInventoryItemRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgStockMovementRepository;
import za.co.handyflow.platform.agriculture.dto.CreateFeedRecordRequest;
import za.co.handyflow.platform.agriculture.dto.FeedRecordResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Recording feed given optionally issues a matching {@link AgStockMovement}
 * (ISSUE) against the referenced {@link AgInventoryItem} and calls
 * {@code AgInventoryItem.issue()} — one transaction, mirroring
 * AgMortalityRecordService's own "history entity + follow-through mutation,
 * same transaction" shape. See {@link AgFeedRecord}'s own Javadoc for why
 * {@code feedType}/{@code costPerKg} are a snapshot of the inventory item at
 * the time, not a live reference.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgFeedRecordService {

    private final AgFeedRecordRepository feedRecordRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final AgInventoryItemRepository inventoryItemRepository;
    private final AgStockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public Page<FeedRecordResponse> getHistoryForAnimal(TenantId tenantId, UUID animalId, Pageable pageable) {
        return feedRecordRepository.findByAnimal(tenantId, animalId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<FeedRecordResponse> getHistoryForGroup(TenantId tenantId, UUID groupId, Pageable pageable) {
        return feedRecordRepository.findByGroup(tenantId, groupId, pageable).map(this::toResponse);
    }

    @Transactional
    public FeedRecordResponse createFeedRecord(TenantId tenantId, CreateFeedRecordRequest req) {
        validateTarget(tenantId, req.animalId(), req.groupId());

        AgFeedRecord record = AgFeedRecord.create(tenantId, req.animalId(), req.groupId(), req.feedDate(),
                req.inventoryItemId(), req.feedType(), req.quantityKg(), req.costPerKg(), req.notes());
        feedRecordRepository.save(record);

        if (req.inventoryItemId() != null) {
            AgInventoryItem item = inventoryItemRepository.findActiveById(tenantId, req.inventoryItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", req.inventoryItemId().toString()));
            item.issue(req.quantityKg());
            AgStockMovement movement = AgStockMovement.create(tenantId, req.inventoryItemId(), "ISSUE",
                    req.feedDate(), req.quantityKg(), req.costPerKg(), "AgFeedRecord", record.getId(),
                    null, null, "Feed issued: " + req.feedType());
            stockMovementRepository.save(movement);
        }

        log.info("Feed record created id={} quantityKg={} tenant={}", record.getId(), req.quantityKg(), tenantId.getValue());
        return toResponse(record);
    }

    private void validateTarget(TenantId tenantId, UUID animalId, UUID groupId) {
        if (animalId != null && animalRepository.findActiveById(tenantId, animalId).isEmpty()) {
            throw new ResourceNotFoundException("Animal", animalId.toString());
        }
        if (groupId != null && groupRepository.findActiveById(tenantId, groupId).isEmpty()) {
            throw new ResourceNotFoundException("Group", groupId.toString());
        }
    }

    private FeedRecordResponse toResponse(AgFeedRecord f) {
        return new FeedRecordResponse(
                f.getId(), f.getAnimalId(), f.getGroupId(), f.getFeedDate(), f.getInventoryItemId(),
                f.getFeedType(), f.getQuantityKg(), f.getCostPerKg(), f.getTotalCost(), f.getNotes(), f.getCreatedAt()
        );
    }
}
