package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/** A person on a committee in a role, for a period. At most one chair at a time (also enforced by a partial unique index). */
@Entity
@Table(name = "legalcompliance_committee_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommitteeMember extends AggregateRoot<CommitteeMember> {

    @Column(name = "committee_id", nullable = false) private UUID committeeId;
    @Column(name = "member_name", nullable = false, length = 255) private String memberName;
    @Column(name = "user_id") private UUID userId;
    @Enumerated(EnumType.STRING) @Column(name = "member_role", nullable = false, length = 10) private MemberRole memberRole;
    @Column(name = "appointed_on", nullable = false) private LocalDate appointedOn;
    @Column(name = "ended_on") private LocalDate endedOn;

    public static CommitteeMember add(TenantId tenantId, UUID committeeId, String name, UUID userId, MemberRole role, LocalDate appointedOn, LocalDate today) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("The member's name is required");
        if (role == null) throw new IllegalArgumentException("A role is required");
        if (appointedOn == null) throw new IllegalArgumentException("The appointment date is required");
        if (appointedOn.isAfter(today)) throw new IllegalArgumentException("An appointment cannot be in the future");
        CommitteeMember m = new CommitteeMember();
        m.initTenantId(tenantId);
        m.committeeId = committeeId;
        m.memberName = name.trim();
        m.userId = userId;
        m.memberRole = role;
        m.appointedOn = appointedOn;
        return m;
    }

    public boolean isActive() { return endedOn == null; }

    /** Counts toward quorum: observers do not. */
    public boolean countsForQuorum() { return isActive() && memberRole != MemberRole.OBSERVER; }

    public void end(LocalDate on, LocalDate today) {
        if (endedOn != null) throw new IllegalStateException("This membership has already ended");
        if (on == null) throw new IllegalArgumentException("The end date is required");
        if (on.isBefore(appointedOn)) throw new IllegalArgumentException("A membership cannot end before it began");
        if (on.isAfter(today)) throw new IllegalArgumentException("An end date cannot be in the future");
        this.endedOn = on;
    }
}
