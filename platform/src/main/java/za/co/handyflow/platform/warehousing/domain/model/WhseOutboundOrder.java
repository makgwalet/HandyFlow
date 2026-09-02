package za.co.handyflow.platform.warehousing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An outbound fulfillment order — a client asking the operator to pick,
 * pack, and ship stock to an end customer. PENDING -&gt; PICKING -&gt;
 * PACKED -&gt; SHIPPED, with CANCELLED reachable from PENDING/PICKING only
 * (once packed, the physical work is essentially done — cancelling at
 * that point is a real-world exception this first pass doesn't model as
 * a one-click action; staff would handle it manually and adjust stock).
 */
@Entity
@Table(name = "whse_outbound_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseOutboundOrder {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "order_reference")
    private String orderReference; // the client's own order/reference number, if supplied

    @Column(name = "ship_to_name")
    private String shipToName;

    @Column(name = "ship_to_address", columnDefinition = "TEXT")
    private String shipToAddress;

    @Column(name = "requested_ship_date")
    private LocalDate requestedShipDate;

    @Column(name = "shipped_date")
    private LocalDate shippedDate;

    @Column(name = "status", nullable = false)
    private String status = "PENDING"; // PENDING | PICKING | PACKED | SHIPPED | CANCELLED

    @Column(name = "carrier")
    private String carrier;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WhseOutboundOrder create(UUID tenantId, UUID clientId, String orderReference, String shipToName,
                                            String shipToAddress, LocalDate requestedShipDate, String notes) {
        WhseOutboundOrder o = new WhseOutboundOrder();
        o.tenantId = tenantId;
        o.clientId = clientId;
        o.orderReference = orderReference;
        o.shipToName = shipToName;
        o.shipToAddress = shipToAddress;
        o.requestedShipDate = requestedShipDate;
        o.status = "PENDING";
        o.notes = notes;
        o.createdAt = Instant.now();
        o.updatedAt = Instant.now();
        return o;
    }

    public void startPicking() {
        if (!"PENDING".equals(status)) {
            throw new IllegalStateException("Only a PENDING order can start picking");
        }
        this.status = "PICKING";
        this.updatedAt = Instant.now();
    }

    public void markPacked() {
        if (!"PICKING".equals(status)) {
            throw new IllegalStateException("Only a PICKING order can be marked packed");
        }
        this.status = "PACKED";
        this.updatedAt = Instant.now();
    }

    public void markShipped(String carrier, String trackingNumber) {
        if (!"PACKED".equals(status)) {
            throw new IllegalStateException("Only a PACKED order can be marked shipped");
        }
        this.status = "SHIPPED";
        this.shippedDate = LocalDate.now();
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (!"PENDING".equals(status) && !"PICKING".equals(status)) {
            throw new IllegalStateException("Cannot cancel an order that is already " + status);
        }
        this.status = "CANCELLED";
        this.updatedAt = Instant.now();
    }
}
