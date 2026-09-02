package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgInventoryItem;
import za.co.handyflow.platform.agriculture.domain.model.AgStockMovement;
import za.co.handyflow.platform.agriculture.domain.repository.AgInventoryItemRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgStockMovementRepository;
import za.co.handyflow.platform.agriculture.dto.*;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD for farm-scoped stock items, plus receive/issue/adjust — each of
 * which both mutates {@code AgInventoryItem.currentQuantity} and appends a
 * matching {@link AgStockMovement} row in the same transaction, the
 * "denormalized current state, append-only trail" shape this module uses
 * throughout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgInventoryItemService {

    private final AgInventoryItemRepository inventoryItemRepository;
    private final AgStockMovementRepository stockMovementRepository;
    private final HrFacade hrFacade;

    @Transactional(readOnly = true)
    public Page<InventoryItemResponse> getItemsForFarm(TenantId tenantId, UUID farmId, Pageable pageable) {
        return inventoryItemRepository.findAllActiveForFarm(tenantId, farmId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getItem(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public InventoryItemResponse createItem(TenantId tenantId, CreateInventoryItemRequest req) {
        AgInventoryItem item = AgInventoryItem.create(tenantId, req.farmId(), req.itemName(), req.category(),
                req.unitOfMeasure(), req.reorderLevel(), req.unitCost(), req.supplier());
        inventoryItemRepository.save(item);
        log.info("Inventory item created id={} farm={} tenant={}", item.getId(), req.farmId(), tenantId.getValue());
        return toResponse(item);
    }

    @Transactional
    public InventoryItemResponse updateItem(TenantId tenantId, UUID id, UpdateInventoryItemRequest req) {
        AgInventoryItem item = findActive(tenantId, id);
        item.update(req.itemName(), req.reorderLevel(), req.unitCost(), req.supplier(), req.notes());
        return toResponse(item);
    }

    @Transactional
    public InventoryItemResponse receive(TenantId tenantId, UUID id, ReceiveInventoryRequest req) {
        AgInventoryItem item = findActive(tenantId, id);
        String performedByName = resolveEmployeeName(tenantId, req.performedBy());
        item.receive(req.quantity(), req.newUnitCost());
        AgStockMovement movement = AgStockMovement.create(tenantId, id, "RECEIPT", LocalDate.now(), req.quantity(),
                req.newUnitCost() != null ? req.newUnitCost() : item.getUnitCost(), null, null,
                req.performedBy(), performedByName, req.notes());
        stockMovementRepository.save(movement);
        return toResponse(item);
    }

    @Transactional
    public InventoryItemResponse issue(TenantId tenantId, UUID id, IssueInventoryRequest req) {
        AgInventoryItem item = findActive(tenantId, id);
        String performedByName = resolveEmployeeName(tenantId, req.performedBy());
        item.issue(req.quantity());
        AgStockMovement movement = AgStockMovement.create(tenantId, id, "ISSUE", LocalDate.now(), req.quantity(),
                item.getUnitCost(), req.referenceType(), req.referenceId(), req.performedBy(), performedByName, req.notes());
        stockMovementRepository.save(movement);
        return toResponse(item);
    }

    @Transactional
    public InventoryItemResponse adjust(TenantId tenantId, UUID id, AdjustInventoryRequest req) {
        AgInventoryItem item = findActive(tenantId, id);
        String performedByName = resolveEmployeeName(tenantId, req.performedBy());
        java.math.BigDecimal delta = req.newQuantity().subtract(item.getCurrentQuantity());
        item.adjust(req.newQuantity());
        AgStockMovement movement = AgStockMovement.create(tenantId, id, "ADJUSTMENT", LocalDate.now(),
                delta.abs(), item.getUnitCost(), null, null, req.performedBy(), performedByName, req.notes());
        stockMovementRepository.save(movement);
        return toResponse(item);
    }

    @Transactional
    public InventoryItemResponse deactivateItem(TenantId tenantId, UUID id) {
        AgInventoryItem item = findActive(tenantId, id);
        item.deactivate();
        return toResponse(item);
    }

    @Transactional
    public InventoryItemResponse reactivateItem(TenantId tenantId, UUID id) {
        AgInventoryItem item = findActive(tenantId, id);
        item.reactivate();
        return toResponse(item);
    }

    @Transactional
    public void deleteItem(TenantId tenantId, UUID id) {
        AgInventoryItem item = findActive(tenantId, id);
        item.softDelete();
        log.info("Inventory item deleted id={} tenant={}", id, tenantId.getValue());
    }

    private AgInventoryItem findActive(TenantId tenantId, UUID id) {
        return inventoryItemRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", id.toString()));
    }

    private String resolveEmployeeName(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        Optional<EmployeeResponse> employee = hrFacade.findEmployeeById(tenantId, employeeId);
        if (employee.isEmpty()) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }
        return employee.get().fullName();
    }

    private InventoryItemResponse toResponse(AgInventoryItem i) {
        return new InventoryItemResponse(
                i.getId(), i.getFarmId(), i.getItemName(), i.getCategory(), i.getUnitOfMeasure(),
                i.getCurrentQuantity(), i.getReorderLevel(), i.getUnitCost(), i.getSupplier(), i.getStatus(),
                i.isBelowReorderLevel(), i.getNotes(), i.getCreatedAt(), i.getUpdatedAt()
        );
    }
}
