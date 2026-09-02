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

    /** One expected line on a new shipment. */
    public record InboundLine(UUID itemId, BigDecimal expectedQty, String notes) {}

    @Transactional
    public WhseInboundShipment createShipment(TenantId tenantId, UUID clientId, String referenceNumber,
                                               LocalDate expectedDate, List<InboundLine> lines, String notes) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("An inbound shipment must contain at least one line");
        }
        clientService.findActive(tenantId, clientId);

        WhseInboundShipment shipment = WhseInboundShipment.create(tenantId.getValue(), clientId, referenceNumber,
                expectedDate, notes);
        shipment = shipmentRepository.save(shipment);

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
    @Transactional
    public WhseInboundShipmentLine receiveLine(TenantId tenantId, UUID shipmentId, UUID lineId, BigDecimal qty,
                                                UUID locationId, UUID recordedBy) {
        WhseInboundShipment shipment = findActive(tenantId, shipmentId);
        WhseInboundShipmentLine line = findLine(tenantId, shipmentId, lineId);

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
