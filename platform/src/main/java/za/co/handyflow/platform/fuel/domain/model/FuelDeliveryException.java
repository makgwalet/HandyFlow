package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A failed delivery attempt (customer closed, no access, refused and so on). Append-only: never edited or deleted. */
@Entity
@Table(name = "fuel_delivery_exceptions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelDeliveryException {

    /** Operational reasons only; the list says nothing about who is at fault or what is owed. */
    public static final Set<String> REASONS = Set.of("SITE_CLOSED", "NO_ACCESS", "RECEIVER_ABSENT", "REFUSED", "SAFETY", "VEHICLE_FAULT", "OTHER");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(nullable = false, length = 16)
    private String kind = "FAILED_ATTEMPT";

    @Column(name = "reason_code", nullable = false, length = 30)
    private String reasonCode;

    @Column(length = 500)
    private String note;

    @Column(name = "litres_returned", nullable = false)
    private BigDecimal litresReturned = BigDecimal.ZERO;

    @Column(name = "rescheduled_to")
    private Instant rescheduledTo;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Column(name = "client_request_id")
    private UUID clientRequestId;

    public static FuelDeliveryException create(TenantId tenantId, UUID deliveryId, String reasonCode, String note, BigDecimal litresReturned,
                                               Instant rescheduledTo, int attemptNumber, UUID by, UUID clientRequestId) {
        if (reasonCode == null || !REASONS.contains(reasonCode)) throw new IllegalArgumentException("Pick a reason for the failed delivery.");
        if ("OTHER".equals(reasonCode) && (note == null || note.isBlank())) throw new IllegalArgumentException("Describe what happened when the reason is Other.");
        if (litresReturned != null && litresReturned.signum() < 0) throw new IllegalArgumentException("Litres returned cannot be negative.");
        FuelDeliveryException e = new FuelDeliveryException();
        e.tenantId = tenantId; e.deliveryId = deliveryId; e.reasonCode = reasonCode;
        e.note = note == null || note.isBlank() ? null : (note.trim().length() > 500 ? note.trim().substring(0, 500) : note.trim());
        e.litresReturned = litresReturned == null ? BigDecimal.ZERO : litresReturned;
        e.rescheduledTo = rescheduledTo; e.attemptNumber = attemptNumber; e.recordedBy = by; e.clientRequestId = clientRequestId;
        return e;
    }
}
