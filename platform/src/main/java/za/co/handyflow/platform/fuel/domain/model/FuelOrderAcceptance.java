package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Who accepted the quotation for the customer, how, and which revision they accepted. One per order; never edited. */
@Entity
@Table(name = "fuel_order_acceptances")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelOrderAcceptance {

    public static final Set<String> CHANNELS = Set.of("EMAIL", "PHONE", "SIGNED_QUOTE", "CUSTOMER_PO", "PORTAL", "OTHER");

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "order_id", nullable = false, updatable = false) private UUID orderId;
    @Column(name = "revision_no", nullable = false, updatable = false) private int revisionNo;
    @Column(name = "accepted_by", nullable = false, updatable = false, length = 120) private String acceptedBy;
    @Column(nullable = false, updatable = false, length = 12) private String channel;
    @Column(updatable = false, length = 80) private String reference;
    @Column(name = "accepted_on", nullable = false, updatable = false) private LocalDate acceptedOn;
    @Column(name = "recorded_by", updatable = false) private UUID recordedBy;
    @Column(name = "recorded_at", nullable = false, updatable = false) private Instant recordedAt = Instant.now();

    public static FuelOrderAcceptance record(TenantId tenantId, UUID orderId, int revisionNo, String acceptedBy, String channel, String reference, LocalDate acceptedOn, LocalDate today, UUID by) {
        String name = acceptedBy == null ? "" : acceptedBy.trim();
        if (name.length() < 2) throw new IllegalArgumentException("Enter the name of the person who accepted the quotation.");
        String ch = channel == null ? "" : channel.trim().toUpperCase();
        if (!CHANNELS.contains(ch)) throw new IllegalArgumentException("Choose how it was accepted: " + String.join(", ", new java.util.TreeSet<>(CHANNELS)) + ".");
        LocalDate on = acceptedOn == null ? today : acceptedOn;
        if (on.isAfter(today)) throw new IllegalArgumentException("The acceptance date cannot be in the future.");
        FuelOrderAcceptance a = new FuelOrderAcceptance();
        a.tenantId = tenantId; a.orderId = orderId; a.revisionNo = revisionNo; a.acceptedBy = name; a.channel = ch;
        a.reference = reference == null || reference.isBlank() ? null : reference.trim(); a.acceptedOn = on; a.recordedBy = by;
        return a;
    }
}
