package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Phase 8e (D03): one shift sent by a pump or POS system, held for review. It changes no stock until a supervisor accepts it. */
@Entity
@Table(name = "fuel_pos_imports")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelPosImport {

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

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

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

    @Column(name = "shift_id")
    private UUID shiftId;

    public static FuelPosImport receive(TenantId tenantId, UUID keyId, String source, String externalId, String payload, String payloadHash, String problems) {
        FuelPosImport i = new FuelPosImport();
        i.tenantId = tenantId; i.keyId = keyId; i.source = source; i.externalId = externalId;
        i.payload = payload; i.payloadHash = payloadHash;
        i.problems = problems == null || problems.isBlank() ? null : problems;
        return i;
    }

    public boolean isPending() { return PENDING.equals(status); }

    public void accept(UUID by, UUID shiftId, String note) {
        if (!isPending()) throw new IllegalStateException("This import is already " + status.toLowerCase() + ".");
        this.status = ACCEPTED; this.decidedBy = by; this.decidedAt = Instant.now(); this.shiftId = shiftId;
        this.decisionNote = note == null || note.isBlank() ? null : note.trim();
    }

    public void reject(UUID by, String reason) {
        if (!isPending()) throw new IllegalStateException("This import is already " + status.toLowerCase() + ".");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        this.status = REJECTED; this.decidedBy = by; this.decidedAt = Instant.now(); this.decisionNote = reason.trim();
    }
}
