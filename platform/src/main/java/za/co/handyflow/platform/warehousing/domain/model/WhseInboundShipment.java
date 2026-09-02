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
 * An inbound shipment / ASN (advance shipping notice) header — a client
 * telling the operator "this stock is coming." Lines (WhseInboundShipment
 * Line) carry the expected-vs-received quantities; this header just
 * carries the shipment's own lifecycle. Receiving is recorded per-line via
 * WhseInboundShipmentService.receiveLine() and only rolls this header's
 * own status forward once every line has been actioned — same "roll the
 * header status up from the lines" shape as
 * ScmService's PO/GRN fully-received logic, arrived at independently
 * (this module has no dependency on `supplychain`).
 */
@Entity
@Table(name = "whse_inbound_shipments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseInboundShipment {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "reference_number")
    private String referenceNumber; // the client's own ASN/PO reference, if supplied

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(name = "status", nullable = false)
    private String status = "EXPECTED"; // EXPECTED | PARTIALLY_RECEIVED | RECEIVED | CANCELLED

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private static final java.util.Set<String> TERMINAL_STATUSES = java.util.Set.of("RECEIVED", "CANCELLED");

    public static WhseInboundShipment create(UUID tenantId, UUID clientId, String referenceNumber,
                                              LocalDate expectedDate, String notes) {
        WhseInboundShipment s = new WhseInboundShipment();
        s.tenantId = tenantId;
        s.clientId = clientId;
        s.referenceNumber = referenceNumber;
        s.expectedDate = expectedDate;
        s.status = "EXPECTED";
        s.notes = notes;
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public void markPartiallyReceived() {
        requireNotTerminal();
        this.status = "PARTIALLY_RECEIVED";
        this.updatedAt = Instant.now();
    }

    public void markReceived() {
        requireNotTerminal();
        this.status = "RECEIVED";
        this.receivedDate = LocalDate.now();
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        requireNotTerminal();
        this.status = "CANCELLED";
        this.updatedAt = Instant.now();
    }

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(this.status);
    }

    private void requireNotTerminal() {
        if (TERMINAL_STATUSES.contains(this.status)) {
            throw new IllegalStateException("Cannot change a shipment that is already " + this.status);
        }
    }
}
