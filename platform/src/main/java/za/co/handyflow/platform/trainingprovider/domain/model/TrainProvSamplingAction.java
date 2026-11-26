package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A corrective action that came out of a draw. Closed (done or cancelled) with a written note; never deleted. */
@Entity(name = "TrainProvSamplingAction")
@Table(name = "trainprov_sampling_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvSamplingAction {
    public static final String OPEN = "OPEN";
    public static final String DONE = "DONE";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "draw_id", nullable = false, updatable = false)
    private UUID drawId;
    @Column(name = "item_id", updatable = false)
    private UUID itemId;
    @Column(nullable = false, updatable = false)
    private String description;
    @Column(name = "owner_name", nullable = false, updatable = false)
    private String ownerName;
    @Column(name = "due_on", updatable = false)
    private LocalDate dueOn;
    @Column(nullable = false)
    private String status = OPEN;
    @Column(name = "closed_on")
    private LocalDate closedOn;
    @Column(name = "closed_note")
    private String closedNote;
    @Column(name = "closed_by")
    private UUID closedBy;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static TrainProvSamplingAction create(TenantId t, UUID drawId, UUID itemId, String description, String owner, LocalDate dueOn, UUID by) {
        TrainProvSamplingAction a = new TrainProvSamplingAction();
        a.tenantId = t.getValue();
        a.drawId = drawId;
        a.itemId = itemId;
        a.description = description.trim();
        a.ownerName = owner.trim();
        a.dueOn = dueOn;
        a.createdBy = by;
        a.createdAt = Instant.now();
        return a;
    }

    public void close(boolean done, LocalDate on, String note, UUID by) {
        if (!OPEN.equals(status)) {
            throw new IllegalStateException("This action is already closed.");
        }
        if (note == null || note.trim().length() < 10) {
            throw new IllegalArgumentException("Give a note of at least 10 characters.");
        }
        this.status = done ? DONE : CANCELLED;
        this.closedOn = on;
        this.closedNote = note.trim();
        this.closedBy = by;
    }
}
