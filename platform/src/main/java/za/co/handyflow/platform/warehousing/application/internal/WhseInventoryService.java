package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseStockMovement;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInventoryRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseStockMovementRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Owns every mutation of a WhseInventory stock position AND the
 * WhseStockMovement audit entry that must accompany it, in one place.
 * This is a deliberate design choice not forced by any collectionsagency
 * precedent (that module has no stock/movement concept at all) — without
 * it, WhseInboundShipmentService and WhseOutboundOrderService would each
 * need to duplicate "mutate the position, then remember to also write a
 * movement row" logic, and it would be easy for a future change to update
 * one and forget the other. Centralizing it here means the ledger
 * (WhseStockMovement) can never drift from the live balance
 * (WhseInventory) as long as callers only ever touch inventory through
 * this service.
 */
@Service
@RequiredArgsConstructor
public class WhseInventoryService {

    private final WhseInventoryRepository repository;
    private final WhseStockMovementRepository movementRepository;

    @Transactional(readOnly = true)
    public List<WhseInventory> listForClient(TenantId tenantId, UUID clientId) {
        return repository.findAllForClient(tenantId.getValue(), clientId);
    }

    @Transactional(readOnly = true)
    public List<WhseInventory> listByItem(TenantId tenantId, UUID clientId, UUID itemId) {
        return repository.findByClientAndItem(tenantId.getValue(), clientId, itemId);
    }

    @Transactional(readOnly = true)
    public WhseInventory get(TenantId tenantId, UUID id) {
        return repository.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("WhseInventory", id.toString()));
    }

    /**
     * Records a goods receipt against a (client, item, location) position,
     * creating the position with a zero starting balance if this is the
     * first time stock has ever been held there. Always paired with a
     * RECEIPT movement.
     */
    @Transactional
    public WhseInventory receiveStock(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId,
                                       BigDecimal qty, String referenceType, UUID referenceId,
                                       String referenceNumber, UUID recordedBy) {
        WhseInventory inv = getOrCreatePosition(tenantId, clientId, itemId, locationId);
        BigDecimal before = inv.getQtyOnHand();
        inv.increaseOnHand(qty);
        inv = repository.save(inv);
        recordMovement(tenantId, clientId, itemId, locationId, "RECEIPT", qty, before, inv.getQtyOnHand(),
                referenceType, referenceId, referenceNumber, null, recordedBy);
        return inv;
    }

    /** Commits stock to an outbound order line — does not itself create a movement (allocation is a reservation, not a physical change; see WhseInventory.allocate()'s own Javadoc). */
    @Transactional
    public WhseInventory allocateStock(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId,
                                        BigDecimal qty) {
        WhseInventory inv = requirePosition(tenantId, clientId, itemId, locationId);
        inv.allocate(qty);
        return repository.save(inv);
    }

    /** Releases a previously allocated quantity without shipping it — e.g. an order line is cancelled. No movement recorded, same reasoning as allocateStock(). */
    @Transactional
    public WhseInventory deallocateStock(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId,
                                          BigDecimal qty) {
        WhseInventory inv = requirePosition(tenantId, clientId, itemId, locationId);
        inv.deallocate(qty);
        return repository.save(inv);
    }

    /** Completes a pick/ship — reduces on-hand AND allocation together. Always paired with a PICK movement (recorded as a negative qtyChange). */
    @Transactional
    public WhseInventory fulfillPick(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId, BigDecimal qty,
                                      String referenceType, UUID referenceId, String referenceNumber,
                                      UUID recordedBy) {
        WhseInventory inv = requirePosition(tenantId, clientId, itemId, locationId);
        BigDecimal before = inv.getQtyOnHand();
        inv.fulfillAllocation(qty);
        inv = repository.save(inv);
        recordMovement(tenantId, clientId, itemId, locationId, "PICK", qty.negate(), before, inv.getQtyOnHand(),
                referenceType, referenceId, referenceNumber, null, recordedBy);
        return inv;
    }

    /** Manual stock adjustment (count correction, damage write-off, ...) — the only way qtyOnHand changes outside the receiving/picking workflows. Always paired with an ADJUSTMENT movement. */
    @Transactional
    public WhseInventory adjust(TenantId tenantId, UUID id, BigDecimal delta, String reason, UUID userId) {
        WhseInventory inv = get(tenantId, id);
        BigDecimal before = inv.getQtyOnHand();
        inv.adjustOnHand(delta);
        inv = repository.save(inv);
        recordMovement(tenantId, inv.getClientId(), inv.getItemId(), inv.getLocationId(), "ADJUSTMENT", delta,
                before, inv.getQtyOnHand(), "ADJUSTMENT", inv.getId(), null, reason, userId);
        return inv;
    }

    WhseInventory getOrCreatePosition(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId) {
        return repository.findPosition(tenantId.getValue(), clientId, itemId, locationId)
                .orElseGet(() -> repository.save(WhseInventory.create(tenantId.getValue(), clientId, itemId, locationId)));
    }

    private WhseInventory requirePosition(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId) {
        return repository.findPosition(tenantId.getValue(), clientId, itemId, locationId)
                .orElseThrow(() -> new IllegalStateException(
                        "No stock position exists for item=" + itemId + " at location=" + locationId
                                + " for this client — nothing to allocate/fulfil"));
    }

    private void recordMovement(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId, String movementType,
                                 BigDecimal qtyChange, BigDecimal qtyBefore, BigDecimal qtyAfter,
                                 String referenceType, UUID referenceId, String referenceNumber, String notes,
                                 UUID recordedBy) {
        movementRepository.save(WhseStockMovement.record(tenantId.getValue(), clientId, itemId, locationId,
                movementType, qtyChange, qtyBefore, qtyAfter, referenceType, referenceId, referenceNumber, notes,
                recordedBy));
    }
}
