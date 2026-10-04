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
import za.co.handyflow.platform.supplychain.application.SupplierFacade;
import za.co.handyflow.platform.supplychain.application.SupplierFacade.SupplierSummary;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final SupplierFacade supplierFacade;

    @Transactional(readOnly = true)
    public Page<InventoryItemResponse> getItemsForFarm(TenantId tenantId, UUID farmId, Pageable pageable) {
        Page<AgInventoryItem> page = inventoryItemRepository.findAllActiveForFarm(tenantId, farmId, pageable);
        Map<UUID, String> names = supplierNames(tenantId, page.getContent().stream().map(AgInventoryItem::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return page.map(i -> toResponse(i, names.get(i.getSupplierId())));
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getItem(TenantId tenantId, UUID id) {
        return toResponse(tenantId, findActive(tenantId, id));
    }

    @Transactional
    public InventoryItemResponse createItem(TenantId tenantId, CreateInventoryItemRequest req) {
        AgInventoryItem item = AgInventoryItem.create(tenantId, req.farmId(), req.itemName(), req.category(),
                req.unitOfMeasure(), req.reorderLevel(), req.unitCost(), req.supplier());
        inventoryItemRepository.save(item);
        log.info("Inventory item created id={} farm={} tenant={}", item.getId(), req.farmId(), tenantId.getValue());
        return toResponse(tenantId, item);
    }

    @Transactional
    public InventoryItemResponse updateItem(TenantId tenantId, UUID id, UpdateInventoryItemRequest req) {
        AgInventoryItem item = findActive(tenantId, id);
        item.update(req.itemName(), req.reorderLevel(), req.unitCost(), req.supplier(), req.notes());
        return toResponse(tenantId, item);
    }

    /** Sets (or, with null, clears) the item's usual supplier. A supplier must exist in Supply Chain and be ACTIVE. */
    @Transactional
    public InventoryItemResponse setUsualSupplier(TenantId tenantId, UUID id, UUID supplierId) {
        AgInventoryItem item = findActive(tenantId, id);
        if (supplierId != null) requireUsableSupplier(tenantId, supplierId, false);
        item.assignSupplier(supplierId);
        log.info("Inventory item usual supplier set id={} supplier={} tenant={}", id, supplierId, tenantId.getValue());
        return toResponse(tenantId, item);
    }

    @Transactional
    public InventoryItemResponse receive(TenantId tenantId, UUID id, ReceiveInventoryRequest req) {
        AgInventoryItem item = findActive(tenantId, id);
        String performedByName = resolveEmployeeName(tenantId, req.performedBy());
        // the supplier chosen for this receipt, else the item's usual one; either must be an ACTIVE Supply Chain supplier (nothing is bought from a blacklisted one)
        UUID supplierId = req.supplierId() != null ? req.supplierId() : item.getSupplierId();
        if (supplierId != null) requireUsableSupplier(tenantId, supplierId, req.supplierId() == null);
        item.receive(req.quantity(), req.newUnitCost());
        AgStockMovement movement = AgStockMovement.create(tenantId, id, "RECEIPT", LocalDate.now(), req.quantity(),
                req.newUnitCost() != null ? req.newUnitCost() : item.getUnitCost(), null, null,
                req.performedBy(), performedByName, req.notes(), supplierId);
        stockMovementRepository.save(movement);
        return toResponse(tenantId, item);
    }

    @Transactional
    public InventoryItemResponse issue(TenantId tenantId, UUID id, IssueInventoryRequest req) {
        AgInventoryItem item = findActive(tenantId, id);
        String performedByName = resolveEmployeeName(tenantId, req.performedBy());
        item.issue(req.quantity());
        AgStockMovement movement = AgStockMovement.create(tenantId, id, "ISSUE", LocalDate.now(), req.quantity(),
                item.getUnitCost(), req.referenceType(), req.referenceId(), req.performedBy(), performedByName, req.notes());
        stockMovementRepository.save(movement);
        return toResponse(tenantId, item);
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
        return toResponse(tenantId, item);
    }

    @Transactional
    public InventoryItemResponse deactivateItem(TenantId tenantId, UUID id) {
        AgInventoryItem item = findActive(tenantId, id);
        item.deactivate();
        return toResponse(tenantId, item);
    }

    @Transactional
    public InventoryItemResponse reactivateItem(TenantId tenantId, UUID id) {
        AgInventoryItem item = findActive(tenantId, id);
        item.reactivate();
        return toResponse(tenantId, item);
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

    private void requireUsableSupplier(TenantId tenantId, UUID supplierId, boolean inherited) {
        SupplierSummary s = supplierFacade.find(tenantId, supplierId).orElseThrow(() -> new IllegalArgumentException(
                (inherited ? "This item's usual supplier" : "The supplier") + " no longer exists in Supply Chain; choose another supplier" + (inherited ? " or clear the usual supplier." : ".")));
        if (!s.isActive()) {
            throw new IllegalArgumentException((inherited ? "This item's usual supplier " : "Supplier ") + s.name() + " is " + (s.status() == null ? "not active" : s.status().toLowerCase())
                    + " and can't be used for new purchases; choose another supplier" + (inherited ? " or clear the usual supplier." : "."));
        }
    }

    /** Names for these suppliers in one lookup; a supplier that no longer exists is simply absent. */
    private Map<UUID, String> supplierNames(TenantId tenantId, Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        supplierFacade.findAll(tenantId, ids).forEach((k, v) -> out.put(k, v.name()));
        return out;
    }

    private InventoryItemResponse toResponse(TenantId tenantId, AgInventoryItem i) {
        return toResponse(i, i.getSupplierId() == null ? null : supplierNames(tenantId, List.of(i.getSupplierId())).get(i.getSupplierId()));
    }

    private InventoryItemResponse toResponse(AgInventoryItem i, String supplierName) {
        return new InventoryItemResponse(
                i.getId(), i.getFarmId(), i.getItemName(), i.getCategory(), i.getUnitOfMeasure(),
                i.getCurrentQuantity(), i.getReorderLevel(), i.getUnitCost(), i.getSupplier(), i.getStatus(),
                i.isBelowReorderLevel(), i.getNotes(), i.getCreatedAt(), i.getUpdatedAt(), i.getSupplierId(), supplierName
        );
    }
}
