package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseAllocationEvent;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseStockMovement;
import za.co.handyflow.platform.warehousing.domain.repository.WhseAllocationEventRepository;
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
    private final WhseAllocationEventRepository allocationEventRepository;

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
     * Records a goods receipt against a (client, item, location) position, creating the position with a zero starting
     * balance if this is the first time stock has ever been held there. Always paired with a RECEIPT movement.
     */
    @Transactional
    public WhseInventory receiveStock(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId,
                                       BigDecimal qty, String referenceType, UUID referenceId,
                                       String referenceNumber, UUID recordedBy) {
        return receiveStock(tenantId, clientId, itemId, locationId, qty, referenceType, referenceId, referenceNumber,
                recordedBy, null);
    }

    /** As above; a non-null idempotencyKey makes a retry of the same request a no-op that returns the current position. */
    @Transactional
    public WhseInventory receiveStock(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId,
                                       BigDecimal qty, String referenceType, UUID referenceId,
                                       String referenceNumber, UUID recordedBy, String idempotencyKey) {
        if (replayed(tenantId, idempotencyKey, clientId, itemId, locationId)) {
            return getOrCreatePosition(tenantId, clientId, itemId, locationId);
        }
        WhseInventory inv = getOrCreatePosition(tenantId, clientId, itemId, locationId);
        BigDecimal before = inv.getQtyOnHand();
        inv.increaseOnHand(qty);
        inv = repository.save(inv);
        recordMovement(tenantId, inv, "RECEIPT", qty, before, referenceType, referenceId, referenceNumber, null,
                recordedBy, idempotencyKey);
        return inv;
    }

    /** Commits stock to an outbound order line. A reservation, not a physical change: it writes an allocation event, not a stock movement. */
    @Transactional
    public WhseInventory allocateStock(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId,
                                        BigDecimal qty) {
        return allocateStock(tenantId, clientId, itemId, locationId, qty, null, null, null, null);
    }

    @Transactional
    public WhseInventory allocateStock(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId,
                                        BigDecimal qty, String referenceType, UUID referenceId,
                                        String referenceNumber, UUID recordedBy) {
        WhseInventory inv = requirePosition(tenantId, clientId, itemId, locationId);
        BigDecimal before = inv.getQtyAllocated();
        inv.allocate(qty);
        inv = repository.save(inv);
        recordAllocation(tenantId, inv, WhseAllocationEvent.ALLOCATE, qty, before, referenceType, referenceId,
                referenceNumber, recordedBy);
        return inv;
    }

    /** Releases a previously allocated quantity without shipping it, e.g. an order line is cancelled. */
    @Transactional
    public WhseInventory deallocateStock(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId,
                                          BigDecimal qty) {
        return deallocateStock(tenantId, clientId, itemId, locationId, qty, null, null, null, null);
    }

    @Transactional
    public WhseInventory deallocateStock(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId,
                                          BigDecimal qty, String referenceType, UUID referenceId,
                                          String referenceNumber, UUID recordedBy) {
        WhseInventory inv = requirePosition(tenantId, clientId, itemId, locationId);
        BigDecimal before = inv.getQtyAllocated();
        inv.deallocate(qty);
        inv = repository.save(inv);
        recordAllocation(tenantId, inv, WhseAllocationEvent.DEALLOCATE, qty, before, referenceType, referenceId,
                referenceNumber, recordedBy);
        return inv;
    }

    /** Completes a pick/ship: reduces on-hand AND allocation together. Writes a PICK movement (negative) and a FULFIL allocation event. */
    @Transactional
    public WhseInventory fulfillPick(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId, BigDecimal qty,
                                      String referenceType, UUID referenceId, String referenceNumber,
                                      UUID recordedBy) {
        return fulfillPick(tenantId, clientId, itemId, locationId, qty, referenceType, referenceId, referenceNumber,
                recordedBy, null);
    }

    @Transactional
    public WhseInventory fulfillPick(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId, BigDecimal qty,
                                      String referenceType, UUID referenceId, String referenceNumber,
                                      UUID recordedBy, String idempotencyKey) {
        if (replayed(tenantId, idempotencyKey, clientId, itemId, locationId)) {
            return requirePosition(tenantId, clientId, itemId, locationId);
        }
        WhseInventory inv = requirePosition(tenantId, clientId, itemId, locationId);
        BigDecimal before = inv.getQtyOnHand();
        BigDecimal allocatedBefore = inv.getQtyAllocated();
        inv.fulfillAllocation(qty);
        inv = repository.save(inv);
        recordMovement(tenantId, inv, "PICK", qty.negate(), before, referenceType, referenceId, referenceNumber, null,
                recordedBy, idempotencyKey);
        recordAllocation(tenantId, inv, WhseAllocationEvent.FULFIL, qty, allocatedBefore, referenceType, referenceId,
                referenceNumber, recordedBy);
        return inv;
    }

    /** Manual stock adjustment (count correction, damage write-off, ...). Always paired with an ADJUSTMENT movement. */
    @Transactional
    public WhseInventory adjust(TenantId tenantId, UUID id, BigDecimal delta, String reason, UUID userId) {
        return adjust(tenantId, id, delta, reason, userId, null);
    }

    @Transactional
    public WhseInventory adjust(TenantId tenantId, UUID id, BigDecimal delta, String reason, UUID userId,
                                 String idempotencyKey) {
        WhseInventory inv = get(tenantId, id);
        if (replayed(tenantId, idempotencyKey, inv.getClientId(), inv.getItemId(), inv.getLocationId())) {
            return inv;
        }
        BigDecimal before = inv.getQtyOnHand();
        inv.adjustOnHand(delta);
        inv = repository.save(inv);
        recordMovement(tenantId, inv, "ADJUSTMENT", delta, before, "ADJUSTMENT", inv.getId(), null, reason, userId,
                idempotencyKey);
        return inv;
    }

    /**
     * Posts an approved cycle-count variance as a COUNT_VARIANCE movement referencing the count session. The idempotency
     * key is the count line, so approving twice posts once.
     */
    @Transactional
    public WhseInventory adjustForCount(TenantId tenantId, UUID inventoryId, BigDecimal delta, UUID sessionId,
                                         String countNumber, String note, UUID userId, String idempotencyKey) {
        WhseInventory inv = get(tenantId, inventoryId);
        if (replayed(tenantId, idempotencyKey, inv.getClientId(), inv.getItemId(), inv.getLocationId())) {
            return inv;
        }
        BigDecimal before = inv.getQtyOnHand();
        inv.adjustOnHand(delta);
        inv = repository.save(inv);
        recordMovement(tenantId, inv, "COUNT_VARIANCE", delta, before, "CYCLE_COUNT", sessionId, countNumber, note, userId,
                idempotencyKey);
        return inv;
    }

    /**
     * Moves unallocated stock between statuses at one location, as two ledger movements (out of the source status,
     * into the destination status) sharing one reference. Allowed: AVAILABLE to QUARANTINE, QUARANTINE to AVAILABLE
     * (release), AVAILABLE or QUARANTINE to DAMAGED. Damaged stock does not return to available without a disposition
     * process (a later slice). Returns the destination position.
     */
    @Transactional
    public WhseInventory changeStatus(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId, String from,
                                       String to, BigDecimal qty, String reason, UUID userId, String idempotencyKey) {
        String type = statusChangeType(from, to);
        if (reason == null || reason.trim().length() < 5) {
            throw new IllegalArgumentException("A reason of at least 5 characters is required");
        }
        if (replayed(tenantId, idempotencyKey, clientId, itemId, locationId)) {
            return destination(tenantId, clientId, itemId, locationId, to);
        }
        WhseInventory source = repository.findPositionWithStatus(tenantId.getValue(), clientId, itemId, locationId, from)
                .orElseThrow(() -> new IllegalStateException("No " + from + " stock at this location to move"));
        WhseInventory dest = repository.findPositionWithStatus(tenantId.getValue(), clientId, itemId, locationId, to)
                .orElseGet(() -> repository.save(WhseInventory.create(tenantId.getValue(), clientId, itemId, locationId, to)));
        UUID changeId = UUID.randomUUID();
        BigDecimal sourceBefore = source.getQtyOnHand();
        BigDecimal destBefore = dest.getQtyOnHand();
        source.decreaseOnHandUnallocated(qty);
        dest.increaseOnHand(qty);
        source = repository.save(source);
        dest = repository.save(dest);
        recordMovement(tenantId, source, type, qty.negate(), sourceBefore, "STATUS_CHANGE", changeId, null, reason,
                userId, idempotencyKey);
        recordMovement(tenantId, dest, type, qty, destBefore, "STATUS_CHANGE", changeId, null, reason, userId,
                idempotencyKey == null ? null : idempotencyKey + ":in");
        return dest;
    }

    static String statusChangeType(String from, String to) {
        if (WhseInventory.STATUS_AVAILABLE.equals(from) && WhseInventory.STATUS_QUARANTINE.equals(to)) return "QUARANTINE";
        if (WhseInventory.STATUS_QUARANTINE.equals(from) && WhseInventory.STATUS_AVAILABLE.equals(to)) return "RELEASE";
        if ((WhseInventory.STATUS_AVAILABLE.equals(from) || WhseInventory.STATUS_QUARANTINE.equals(from))
                && WhseInventory.STATUS_DAMAGED.equals(to)) return "DAMAGE";
        throw new IllegalArgumentException("A status change from " + from + " to " + to + " is not allowed");
    }

    private WhseInventory destination(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId, String status) {
        return repository.findPositionWithStatus(tenantId.getValue(), clientId, itemId, locationId, status)
                .orElseThrow(() -> new IllegalStateException("No " + status + " position found for the replayed request"));
    }

    WhseInventory getOrCreatePosition(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId) {
        return repository.findPosition(tenantId.getValue(), clientId, itemId, locationId)
                .orElseGet(() -> repository.save(WhseInventory.create(tenantId.getValue(), clientId, itemId, locationId)));
    }

    private WhseInventory requirePosition(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId) {
        return repository.findPosition(tenantId.getValue(), clientId, itemId, locationId)
                .orElseThrow(() -> new IllegalStateException(
                        "No available stock position exists for item=" + itemId + " at location=" + locationId
                                + " for this client — nothing to allocate/fulfil"));
    }

    /**
     * True when this idempotency key has already been used. A key reused for a different client, item or location is
     * a caller error and is refused rather than silently ignored.
     */
    private boolean replayed(TenantId tenantId, String key, UUID clientId, UUID itemId, UUID locationId) {
        if (key == null || key.isBlank()) return false;
        return movementRepository.findFirstByTenantIdAndIdempotencyKey(tenantId.getValue(), key)
                .map(m -> {
                    if (!m.getClientId().equals(clientId) || !m.getItemId().equals(itemId)
                            || !m.getLocationId().equals(locationId)) {
                        throw new IllegalStateException("Idempotency key '" + key + "' was already used for a different request");
                    }
                    return true;
                }).orElse(false);
    }

    private void recordMovement(TenantId tenantId, WhseInventory inv, String movementType, BigDecimal qtyChange,
                                 BigDecimal qtyBefore, String referenceType, UUID referenceId, String referenceNumber,
                                 String notes, UUID recordedBy, String idempotencyKey) {
        movementRepository.save(WhseStockMovement.record(tenantId.getValue(), inv.getClientId(), inv.getItemId(),
                inv.getLocationId(), movementType, qtyChange, qtyBefore, inv.getQtyOnHand(), referenceType,
                referenceId, referenceNumber, notes, recordedBy, inv.getStatus(), idempotencyKey));
    }

    private void recordAllocation(TenantId tenantId, WhseInventory inv, String eventType, BigDecimal qty,
                                   BigDecimal allocatedBefore, String referenceType, UUID referenceId,
                                   String referenceNumber, UUID recordedBy) {
        allocationEventRepository.save(WhseAllocationEvent.record(tenantId.getValue(), inv.getClientId(),
                inv.getItemId(), inv.getLocationId(), eventType, qty, allocatedBefore, inv.getQtyAllocated(),
                referenceType, referenceId, referenceNumber, recordedBy));
    }
}
