package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A tank level sent by a gauge system and held for a person to accept (it becomes a dip reading) or reject. */
@Entity
@Table(name = "fuel_gauge_readings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelGaugeReading {

    public static final String PENDING = "PENDING", ACCEPTED = "ACCEPTED", REJECTED = "REJECTED";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "key_id", updatable = false)
    private UUID keyId;

    @Column(nullable = false, updatable = false, length = 40)
    private String source;

    @Column(name = "external_id", nullable = false, updatable = false, length = 100)
    private String externalId;

    @Column(name = "tank_label", nullable = false, updatable = false, length = 60)
    private String tankLabel;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal litres;

    @Column(name = "read_at", nullable = false, updatable = false)
    private Instant readAt;

    @Column(name = "sender_name", updatable = false, length = 120)
    private String senderName;

    @Column(name = "payload_hash", nullable = false, updatable = false, length = 64)
    private String payloadHash;

    @Column(columnDefinition = "TEXT")
    private String problems;

    @Column(nullable = false, length = 10)
    private String status = PENDING;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "dip_reading_id")
    private UUID dipReadingId;

    public static FuelGaugeReading receive(TenantId tenantId, UUID keyId, String source, String externalId, String tankLabel, BigDecimal litres,
                                           Instant readAt, String senderName, String payloadHash, String problems) {
        FuelGaugeReading g = new FuelGaugeReading();
        g.tenantId = tenantId; g.keyId = keyId; g.source = source; g.externalId = externalId; g.tankLabel = tankLabel; g.litres = litres;
        g.readAt = readAt; g.senderName = senderName == null || senderName.isBlank() ? null : senderName.trim();
        g.payloadHash = payloadHash; g.problems = problems == null || problems.isBlank() ? null : problems;
        return g;
    }

    public boolean isPending() { return PENDING.equals(status); }

    public void accept(UUID by, UUID dipReadingId, String note) {
        if (!isPending()) throw new IllegalStateException("This reading is already " + status.toLowerCase() + ".");
        this.status = ACCEPTED; this.decidedBy = by; this.decidedAt = Instant.now(); this.dipReadingId = dipReadingId;
        this.decisionNote = note == null || note.isBlank() ? null : note.trim();
    }

    public void reject(UUID by, String reason) {
        if (!isPending()) throw new IllegalStateException("This reading is already " + status.toLowerCase() + ".");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        this.status = REJECTED; this.decidedBy = by; this.decidedAt = Instant.now(); this.decisionNote = reason.trim();
    }
}
