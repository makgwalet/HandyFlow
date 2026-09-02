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
        WhseOutboundOrder order = findActive(tenantId, orderId);
        order.startPicking();

        List<WhseOutboundOrderLine> lines = lineRepository.findByOrder(tenantId.getValue(), orderId);
        for (WhseOutboundOrderLine line : lines) {
            List<WhseInventory> positions = inventoryRepository.findByClientAndItem(tenantId.getValue(),
                    order.getClientId(), line.getItemId());
            WhseInventory best = positions.stream()
                    .filter(p -> p.available().compareTo(line.getQtyOrdered()) >= 0)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Insufficient available stock for item " + line.getItemId() + " — required "
                                    + line.getQtyOrdered() + ", no single location can cover it in full"));
            inventoryService.allocateStock(tenantId, order.getClientId(), line.getItemId(), best.getLocationId(),
                    line.getQtyOrdered());
            line.allocate(best.getLocationId());
            lineRepository.save(line);
        }
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
        order.markShipped(carrier, trackingNumber);

        List<WhseOutboundOrderLine> lines = lineRepository.findByOrder(tenantId.getValue(), orderId);
        for (WhseOutboundOrderLine line : lines) {
            if (line.getLocationId() == null) {
                throw new IllegalStateException(
                        "Line " + line.getId() + " was never allocated a location — cannot ship");
            }
            inventoryService.fulfillPick(tenantId, order.getClientId(), line.getItemId(), line.getLocationId(),
                    line.getQtyOrdered(), "OUTBOUND_ORDER", order.getId(), order.getOrderReference(), recordedBy);
        }
        return orderRepository.save(order);
    }

    /** Cancels a PENDING or PICKING order, releasing any allocation already made. */
    @Transactional
    public WhseOutboundOrder cancel(TenantId tenantId, UUID orderId) {
        WhseOutboundOrder order = findActive(tenantId, orderId);
        if ("PICKING".equals(order.getStatus())) {
            List<WhseOutboundOrderLine> lines = lineRepository.findByOrder(tenantId.getValue(), orderId);
            for (WhseOutboundOrderLine line : lines) {
                if (line.getLocationId() != null) {
                    inventoryService.deallocateStock(tenantId, order.getClientId(), line.getItemId(),
                            line.getLocationId(), line.getQtyOrdered());
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
