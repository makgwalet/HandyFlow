package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/** A decision taken, or an action assigned (an action needs an owner and a due date). Actions are OPEN → DONE. */
@Entity
@Table(name = "legalcompliance_committee_resolutions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommitteeResolution extends AggregateRoot<CommitteeResolution> {

    public static final int MIN_TEXT = 15;

    @Column(name = "meeting_id", nullable = false) private UUID meetingId;
    @Column(name = "committee_id", nullable = false) private UUID committeeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private ResolutionKind kind;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "owner_name", length = 255) private String ownerName;
    @Column(name = "due_date") private LocalDate dueDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private ResolutionStatus status = ResolutionStatus.OPEN;
    @Column(name = "completed_on") private LocalDate completedOn;
    @Column(columnDefinition = "TEXT") private String note;

    public static CommitteeResolution create(TenantId tenantId, UUID meetingId, UUID committeeId, ResolutionKind kind, String description, String owner, LocalDate due, LocalDate meetingDate) {
        CommitteeResolution r = new CommitteeResolution();
        r.initTenantId(tenantId);
        r.meetingId = meetingId;
        r.committeeId = committeeId;
        r.apply(kind, description, owner, due, meetingDate);
        return r;
    }

    public void update(ResolutionKind kind, String description, String owner, LocalDate due, LocalDate meetingDate) {
        if (status == ResolutionStatus.DONE) throw new IllegalStateException("A completed action cannot be edited");
        apply(kind, description, owner, due, meetingDate);
    }

    private void apply(ResolutionKind kind, String description, String owner, LocalDate due, LocalDate meetingDate) {
        if (kind == null) throw new IllegalArgumentException("Say whether this is a decision or an action");
        if (description == null || description.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Describe it (at least " + MIN_TEXT + " characters)");
        if (kind == ResolutionKind.ACTION) {
            if (owner == null || owner.isBlank()) throw new IllegalArgumentException("An action needs an owner");
            if (due == null) throw new IllegalArgumentException("An action needs a due date");
            if (due.isBefore(meetingDate)) throw new IllegalArgumentException("An action cannot be due before the meeting");
        }
        this.kind = kind;
        this.description = description.trim();
        this.ownerName = kind == ResolutionKind.ACTION ? owner.trim() : null;
        this.dueDate = kind == ResolutionKind.ACTION ? due : null;
    }

    public void complete(LocalDate on, String note, LocalDate today) {
        if (kind != ResolutionKind.ACTION) throw new IllegalStateException("Only an action can be completed");
        if (status == ResolutionStatus.DONE) throw new IllegalStateException("This action is already done");
        if (on == null) throw new IllegalArgumentException("The completion date is required");
        if (on.isAfter(today)) throw new IllegalArgumentException("An action cannot be completed in the future");
        this.completedOn = on;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.status = ResolutionStatus.DONE;
    }
}
