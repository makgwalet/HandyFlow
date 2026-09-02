package za.co.handyflow.platform.warehousing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "whse_inbound_shipment_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseInboundShipmentLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "expected_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal expectedQty;

    @Column(name = "received_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal receivedQty = BigDecimal.ZERO;

    @Column(name = "location_id")
    private UUID locationId; // where it was put away — set when received, null (not yet decided) beforehand

    @Column(name = "notes")
    private String notes;

    public static WhseInboundShipmentLine create(UUID tenantId, UUID shipmentId, UUID itemId,
                                                  BigDecimal expectedQty, String notes) {
        if (expectedQty == null || expectedQty.signum() <= 0) {
            throw new IllegalArgumentException("expectedQty must be positive");
        }
        WhseInboundShipmentLine l = new WhseInboundShipmentLine();
        l.tenantId = tenantId;
        l.shipmentId = shipmentId;
        l.itemId = itemId;
        l.expectedQty = expectedQty;
        l.receivedQty = BigDecimal.ZERO;
        l.notes = notes;
        return l;
    }

    /** Records a receipt against this line — cumulative, since a line can be received in more than one pass (e.g. a short delivery followed by a top-up). */
    public void receive(BigDecimal qty, UUID locationId) {
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (locationId == null) {
            throw new IllegalArgumentException("locationId is required — every receipt must be put away somewhere");
        }
        this.receivedQty = this.receivedQty.add(qty);
        this.locationId = locationId;
    }

    public boolean isFullyReceived() {
        return receivedQty.compareTo(expectedQty) >= 0;
    }

    public BigDecimal outstandingQty() {
        BigDecimal outstanding = expectedQty.subtract(receivedQty);
        return outstanding.signum() > 0 ? outstanding : BigDecimal.ZERO;
    }
}
