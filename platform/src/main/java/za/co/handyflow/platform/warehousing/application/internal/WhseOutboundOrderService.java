package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrder;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrderLine;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInventoryRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseOutboundOrderLineRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseOutboundOrderRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The fulfilment workflow: create -&gt; startPicking (allocates stock) -&gt;
 * markLinePicked (progress tracking) -&gt; markPacked -&gt; markShipped
 * (fulfils the allocation, posting PICK movements) -&gt; or cancel
 * (releases any allocation first).
 */
@Service
@RequiredArgsConstructor
public class WhseOutboundOrderService {

    private final WhseOutboundOrderRepository orderRepository;
    private final WhseOutboundOrderLineRepository lineRepository;
    private final WhseInventoryRepository inventoryRepository;
    private final WhseInventoryService inventoryService;
    private final WhseClientService clientService;
    private final WhseLotAllocationService lotAllocation;
    private final WhseTaskService taskService;
    private final WhseSerialService serialService;

    /** One ordered line on a new outbound order. */
    public record OutboundLine(UUID itemId, BigDecimal qtyOrdered, String notes) {}

    @Transactional
    public WhseOutboundOrder createOrder(TenantId tenantId, UUID clientId, String orderReference, String shipToName,
                                          String shipToAddress, LocalDate requestedShipDate,
                                          List<OutboundLine> lines, String notes) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("An outbound order must contain at least one line");
        }
        clientService.findActive(tenantId, clientId);

        WhseOutboundOrder order = WhseOutboundOrder.create(tenantId.getValue(), clientId, orderReference,
                shipToName, shipToAddress, requestedShipDate, notes);
        order = orderRepository.save(order);

        for (OutboundLine line : lines) {
            lineRepository.save(WhseOutboundOrderLine.create(tenantId.getValue(), order.getId(), line.itemId(),
                    line.qtyOrdered(), line.notes()));
        }
        return order;
    }

    /**
     * Moves the order to PICKING and allocates stock for every line, each
     * against whichever single location currently holds enough available
     * quantity to cover that line in full. This first pass does not split
     * one line's allocation across multiple locations — a line whose best
     * single location can't cover the full ordered quantity fails outright
     * (no partial allocation) so staff can resolve the shortfall manually
     * rather than the system silently under-allocating. Lot/FEFO-aware
     * allocation, as `supplychain` does internally, is out of scope here:
     * WhseInventory carries no lot/expiry tracking at all in this first
     * pass (see WhseInventory's own Javadoc).
     */
    @Transactional
    public WhseOutboundOrder startPicking(TenantId tenantId, UUID orderId) {
        return startPicking(tenantId, orderId, null);
    }

    @Transactional
    public WhseOutboundOrder startPicking(TenantId tenantId, UUID orderId, UUID recordedBy) {
        return startPicking(tenantId, orderId, recordedBy, false);
    }

    /**
     * Starts picking. A line of an item that is not lot tracked is taken from one location when one holds it all, otherwise it is split across
     * locations (biggest positions first). With {@code allowPartial}, a line the stock cannot cover in full is allocated what there is and the rest
     * is back-ordered; without it, any shortage fails the whole start and nothing is allocated. An order with nothing to allocate at all fails either way.
     */
    @Transactional
    public WhseOutboundOrder startPicking(TenantId tenantId, UUID orderId, UUID recordedBy, boolean allowPartial) {
        WhseOutboundOrder order = findActive(tenantId, orderId);
        order.startPicking();

        List<WhseOutboundOrderLine> lines = lineRepository.findByOrder(tenantId.getValue(), orderId);
        for (WhseOutboundOrderLine line : lines) {
            if (lotAllocation.isLotTracked(tenantId.getValue(), line.getItemId())) {
                // Lot-tracked items are allocated lot by lot, first expiry first, possibly across several locations.
                if (!allowPartial) {
                    line.allocate(lotAllocation.allocate(tenantId, order.getId(), order.getOrderReference(), line.getId(), order.getClientId(),
                            line.getItemId(), line.getQtyOrdered(), recordedBy));
                } else {
                    WhseLotAllocationService.Split split = lotAllocation.allocateUpTo(tenantId, order.getId(), order.getOrderReference(), line.getId(),
                            order.getClientId(), line.getItemId(), line.getQtyOrdered(), recordedBy);
                    if (split.firstLocationId() != null) line.allocate(split.firstLocationId());
                    if (split.unallocated().signum() > 0) line.backorder(split.unallocated());
                }
                lineRepository.save(line);
                continue;
            }
            List<WhseInventory> positions = inventoryRepository.findByClientAndItem(tenantId.getValue(),
                    order.getClientId(), line.getItemId());
            WhseInventory best = positions.stream()
                    .filter(p -> p.available().compareTo(line.getQtyOrdered()) >= 0)
                    .findFirst()
                    .orElse(null);
            if (best != null) {
                inventoryService.allocateStock(tenantId, order.getClientId(), line.getItemId(), best.getLocationId(),
                        line.getQtyOrdered(), "OUTBOUND_ORDER", order.getId(), order.getOrderReference(), recordedBy);
                line.allocate(best.getLocationId());
            } else {
                WhseLotAllocationService.Split split = lotAllocation.allocateSplit(tenantId, order.getId(), order.getOrderReference(), line.getId(),
                        order.getClientId(), line.getItemId(), line.getQtyOrdered(), recordedBy, allowPartial, java.util.Set.of());
                if (split.firstLocationId() != null) line.allocate(split.firstLocationId());
                if (split.unallocated().signum() > 0) line.backorder(split.unallocated());
            }
            lineRepository.save(line);
        }
        if (lines.stream().allMatch(l -> l.qtyToShip().signum() == 0)) {
            throw new IllegalStateException("No stock is available for any line of this order, so picking cannot start.");
        }
        // The pick tasks are built from the lines' locations and the lot allocations, so the lines must be written first.
        lineRepository.flush();
        taskService.createPickTasks(tenantId, order.getId());
        return orderRepository.save(order);
    }

    /** Records picking progress on one line — informational only in this first pass, see markShipped()'s Javadoc. */
    @Transactional
    public WhseOutboundOrderLine markLinePicked(TenantId tenantId, UUID orderId, UUID lineId, BigDecimal qty) {
        WhseOutboundOrderLine line = findLine(tenantId, orderId, lineId);
        line.markPicked(qty);
        return lineRepository.save(line);
    }

    @Transactional
    public WhseOutboundOrder markPacked(TenantId tenantId, UUID orderId) {
        WhseOutboundOrder order = findActive(tenantId, orderId);
        requirePicksFinished(tenantId, orderId);
        order.markPacked();
        return orderRepository.save(order);
    }

    /**
     * Ships the order: fulfils each line's full allocated (= ordered)
     * quantity, posting a PICK movement per line, then moves the order to
     * SHIPPED. Deliberately fulfils the ORDERED quantity rather than
     * whatever qtyPicked happens to tally to — markLinePicked() is treated
     * here as an informational picking-progress signal for staff, not the
     * authoritative fulfilled quantity. A genuine short-shipment (staff
     * physically picked less than ordered) is not modeled in this first
     * pass and would need a manual WhseInventoryService.adjust() afterward
     * to true up the balance. Flagged as a simplification, not silently
     * resolved — see module status doc.
     */
    @Transactional
    public WhseOutboundOrder markShipped(TenantId tenantId, UUID orderId, String carrier, String trackingNumber,
                                          UUID recordedBy) {
        WhseOutboundOrder order = findActive(tenantId, orderId);
        requirePicksFinished(tenantId, orderId);
        List<WhseOutboundOrderLine> lines = lineRepository.findByOrder(tenantId.getValue(), orderId);
        if (lines.stream().allMatch(l -> l.qtyToShip().signum() == 0)) {
            throw new IllegalStateException("Every line was short-picked down to nothing, so there is nothing to ship. Cancel the order instead.");
        }
        serialService.requireCaptured(tenantId, orderId); // serial-tracked lines need a serial for every unit leaving
        order.markShipped(carrier, trackingNumber);
        serialService.markShipped(tenantId, orderId, recordedBy);
        // Lot-tracked lines ship from their lot allocations, one PICK movement per lot.
        lotAllocation.fulfil(tenantId, order.getId(), order.getOrderReference(), recordedBy);
        for (WhseOutboundOrderLine line : lines) {
            if (line.qtyToShip().signum() == 0) continue; // given up entirely after a short pick, or all back-ordered
            if (line.getLocationId() == null) {
                throw new IllegalStateException(
                        "Line " + line.getId() + " was never allocated a location — cannot ship");
            }
            if (lotAllocation.isLotTracked(tenantId.getValue(), line.getItemId()) || lotAllocation.hasAllocations(tenantId.getValue(), line.getId())) {
                continue; // shipped from its allocation rows above
            }
            inventoryService.fulfillPick(tenantId, order.getClientId(), line.getItemId(), line.getLocationId(),
                    line.qtyToShip(), "OUTBOUND_ORDER", order.getId(), order.getOrderReference(), recordedBy);
        }
        return orderRepository.save(order);
    }

    /**
     * Gives back an order's lot allocations and allocates them again, first expiry first. For a PICKING order whose lots were blocked or
     * went out of date after allocation. Lines of items that are not lot tracked keep their allocation.
     */
    @Transactional
    public WhseOutboundOrder reallocateLots(TenantId tenantId, UUID orderId, UUID recordedBy) {
        WhseOutboundOrder order = findActive(tenantId, orderId);
        if (!"PICKING".equals(order.getStatus())) {
            throw new IllegalStateException("Only an order that is being picked can have its lots re-allocated (this one is " + order.getStatus() + ")");
        }
        lotAllocation.release(tenantId, order.getId(), order.getOrderReference(), recordedBy);
        taskService.cancelPickTasks(tenantId, order.getId(), "Lots re-allocated");
        for (WhseOutboundOrderLine line : lineRepository.findByOrder(tenantId.getValue(), orderId)) {
            if (!lotAllocation.isLotTracked(tenantId.getValue(), line.getItemId()) || line.qtyToShip().signum() == 0) continue;
            line.allocate(lotAllocation.allocate(tenantId, order.getId(), order.getOrderReference(), line.getId(), order.getClientId(),
                    line.getItemId(), line.qtyToShip(), recordedBy));
            lineRepository.save(line);
        }
        lineRepository.flush();
        taskService.createPickTasks(tenantId, order.getId());
        return order;
    }

    /** Cancels a PENDING or PICKING order, releasing any allocation already made. */
    @Transactional
    public WhseOutboundOrder cancel(TenantId tenantId, UUID orderId) {
        return cancel(tenantId, orderId, null);
    }

    @Transactional
    public WhseOutboundOrder cancel(TenantId tenantId, UUID orderId, UUID recordedBy) {
        WhseOutboundOrder order = findActive(tenantId, orderId);
        if ("PICKING".equals(order.getStatus())) {
            serialService.releasePicked(tenantId, order.getId(), recordedBy);
            lotAllocation.release(tenantId, order.getId(), order.getOrderReference(), recordedBy);
            taskService.cancelPickTasks(tenantId, order.getId(), "Order cancelled");
            List<WhseOutboundOrderLine> lines = lineRepository.findByOrder(tenantId.getValue(), orderId);
            for (WhseOutboundOrderLine line : lines) {
                if (line.getLocationId() != null && line.qtyToShip().signum() > 0 && !lotAllocation.isLotTracked(tenantId.getValue(), line.getItemId())
                        && !lotAllocation.hasAllocations(tenantId.getValue(), line.getId())) {
                    inventoryService.deallocateStock(tenantId, order.getClientId(), line.getItemId(),
                            line.getLocationId(), line.qtyToShip(), "OUTBOUND_ORDER", order.getId(),
                            order.getOrderReference(), recordedBy);
                }
            }
        }
        order.cancel();
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Page<WhseOutboundOrder> listForClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        return orderRepository.findByClient(tenantId.getValue(), clientId, pageable);
    }

    @Transactional(readOnly = true)
    public WhseOutboundOrder get(TenantId tenantId, UUID orderId) {
        return findActive(tenantId, orderId);
    }

    @Transactional(readOnly = true)
    public List<WhseOutboundOrderLine> listLines(TenantId tenantId, UUID orderId) {
        return lineRepository.findByOrder(tenantId.getValue(), orderId);
    }

    /** An order whose pick tasks are not all done (open, held, or short) cannot be packed or shipped. Orders without tasks are unaffected. */
    private void requirePicksFinished(TenantId tenantId, UUID orderId) {
        int open = taskService.unfinishedPickTasks(tenantId, orderId);
        if (open > 0) {
            throw new IllegalStateException(open + " pick task" + (open == 1 ? " is" : "s are") + " not finished (including any short picks). Finish them, or re-allocate or cancel the order.");
        }
    }

    private WhseOutboundOrder findActive(TenantId tenantId, UUID orderId) {
        return orderRepository.findByTenantAndId(tenantId.getValue(), orderId)
                .orElseThrow(() -> new ResourceNotFoundException("WhseOutboundOrder", orderId.toString()));
    }

    private WhseOutboundOrderLine findLine(TenantId tenantId, UUID orderId, UUID lineId) {
        WhseOutboundOrderLine line = lineRepository.findByTenantAndId(tenantId.getValue(), lineId)
                .orElseThrow(() -> new ResourceNotFoundException("WhseOutboundOrderLine", lineId.toString()));
        if (!line.getOrderId().equals(orderId)) {
            throw new ResourceNotFoundException("WhseOutboundOrderLine", lineId.toString());
        }
        return line;
    }
}
