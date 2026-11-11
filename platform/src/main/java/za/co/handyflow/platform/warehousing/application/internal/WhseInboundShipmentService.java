package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipment;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipmentLine;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInboundShipmentLineRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInboundShipmentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The receiving workflow: a client tells the operator stock is coming
 * (createShipment), and staff record each line's actual receipt against a
 * put-away location (receiveLine) — which is the point stock actually
 * enters WhseInventory (via WhseInventoryService.receiveStock(), which
 * also writes the RECEIPT movement). The shipment header's own status is
 * rolled forward automatically from its lines rather than set directly by
 * a caller, so it can never say RECEIVED while a line still has an
 * outstanding quantity.
 */
@Service
@RequiredArgsConstructor
public class WhseInboundShipmentService {

    private final WhseInboundShipmentRepository shipmentRepository;
    private final WhseInboundShipmentLineRepository lineRepository;
    private final WhseInventoryService inventoryService;
    private final WhseClientService clientService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private WhsePackageService packages;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private WhseDocumentWarehouse documentWarehouse;

    private void requireRoom(TenantId tenantId, WhsePackageRules.Limit limit) {
        if (packages != null) packages.requireRoomFor(tenantId, limit);
    }

    private void recordTransaction(TenantId tenantId, String type, UUID id) {
        if (packages != null) packages.recordTransaction(tenantId, type, id);
    }

    /** One expected line on a new shipment. */
    public record InboundLine(UUID itemId, BigDecimal expectedQty, String notes) {}

    @Transactional
    public WhseInboundShipment createShipment(TenantId tenantId, UUID clientId, String referenceNumber,
                                               LocalDate expectedDate, List<InboundLine> lines, String notes) {
        return createShipment(tenantId, clientId, referenceNumber, expectedDate, lines, notes, null);
    }

    /** warehouseId is the warehouse the stock is coming to; null means the tenant's default warehouse. */
    @Transactional
    public WhseInboundShipment createShipment(TenantId tenantId, UUID clientId, String referenceNumber,
                                               LocalDate expectedDate, List<InboundLine> lines, String notes, UUID warehouseId) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("An inbound shipment must contain at least one line");
        }
        requireRoom(tenantId, WhsePackageRules.Limit.MONTHLY_TRANSACTIONS);
        clientService.findActive(tenantId, clientId);

        WhseInboundShipment shipment = WhseInboundShipment.create(tenantId.getValue(), clientId, referenceNumber,
                expectedDate, notes);
        shipment = shipmentRepository.save(shipment);
        if (documentWarehouse != null) documentWarehouse.assign(tenantId, WhseDocumentWarehouse.Kind.INBOUND, shipment.getId(), warehouseId);
        recordTransaction(tenantId, "RECEIPT", shipment.getId());

        for (InboundLine line : lines) {
            lineRepository.save(WhseInboundShipmentLine.create(tenantId.getValue(), shipment.getId(),
                    line.itemId(), line.expectedQty(), line.notes()));
        }
        return shipment;
    }

    /**
     * Records receipt of {@code qty} of one line's item, put away at
     * {@code locationId}. Cumulative — a line can be received across more
     * than one pass (short delivery followed by a top-up). Posts the
     * quantity into WhseInventory immediately and rolls the shipment
     * header's status forward.
     */
    /**
     * The quick receive posts stock with no lot. A lot-tracked item must be received with its lot number
     * (and expiry date where tracked), which the receipts endpoint takes; refuse here with that instruction
     * rather than letting the database reject the posting.
     */
    private void requireNotLotTracked(TenantId tenantId, UUID itemId) {
        Boolean tracked = jdbc.queryForObject("SELECT lot_tracked FROM whse_items WHERE id = ? AND tenant_id = ?",
                Boolean.class, itemId, tenantId.getValue());
        if (Boolean.TRUE.equals(tracked)) {
            throw new IllegalArgumentException("This item is lot tracked, so it must be received with a lot number. "
                    + "Use Receive on the shipment's receiving screen instead of the quick receive.");
        }
    }

    @Transactional
    public WhseInboundShipmentLine receiveLine(TenantId tenantId, UUID shipmentId, UUID lineId, BigDecimal qty,
                                                UUID locationId, UUID recordedBy) {
        WhseInboundShipment shipment = findActive(tenantId, shipmentId);
        WhseInboundShipmentLine line = findLine(tenantId, shipmentId, lineId);
        requireNotLotTracked(tenantId, line.getItemId());

        line.receive(qty, locationId);
        lineRepository.save(line);

        inventoryService.receiveStock(tenantId, shipment.getClientId(), line.getItemId(), locationId, qty,
                "INBOUND_SHIPMENT", shipment.getId(), shipment.getReferenceNumber(), recordedBy);

        rollShipmentStatus(tenantId, shipment);
        return line;
    }

    @Transactional
    public WhseInboundShipment cancel(TenantId tenantId, UUID shipmentId) {
        WhseInboundShipment shipment = findActive(tenantId, shipmentId);
        shipment.cancel();
        return shipmentRepository.save(shipment);
    }

    @Transactional(readOnly = true)
    public Page<WhseInboundShipment> listForClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        return shipmentRepository.findByClient(tenantId.getValue(), clientId, pageable);
    }

    @Transactional(readOnly = true)
    public WhseInboundShipment get(TenantId tenantId, UUID shipmentId) {
        return findActive(tenantId, shipmentId);
    }

    @Transactional(readOnly = true)
    public List<WhseInboundShipmentLine> listLines(TenantId tenantId, UUID shipmentId) {
        return lineRepository.findByShipment(tenantId.getValue(), shipmentId);
    }

    private void rollShipmentStatus(TenantId tenantId, WhseInboundShipment shipment) {
        if (shipment.isTerminal()) {
            return; // already RECEIVED or CANCELLED — nothing further to roll forward
        }
        List<WhseInboundShipmentLine> lines = lineRepository.findByShipment(tenantId.getValue(), shipment.getId());
        boolean allFullyReceived = !lines.isEmpty() && lines.stream().allMatch(WhseInboundShipmentLine::isFullyReceived);
        if (allFullyReceived) {
            shipment.markReceived();
        } else {
            shipment.markPartiallyReceived();
        }
        shipmentRepository.save(shipment);
    }

    private WhseInboundShipment findActive(TenantId tenantId, UUID shipmentId) {
        return shipmentRepository.findByTenantAndId(tenantId.getValue(), shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("WhseInboundShipment", shipmentId.toString()));
    }

    private WhseInboundShipmentLine findLine(TenantId tenantId, UUID shipmentId, UUID lineId) {
        WhseInboundShipmentLine line = lineRepository.findByTenantAndId(tenantId.getValue(), lineId)
                .orElseThrow(() -> new ResourceNotFoundException("WhseInboundShipmentLine", lineId.toString()));
        if (!line.getShipmentId().equals(shipmentId)) {
            throw new ResourceNotFoundException("WhseInboundShipmentLine", lineId.toString());
        }
        return line;
    }
}
