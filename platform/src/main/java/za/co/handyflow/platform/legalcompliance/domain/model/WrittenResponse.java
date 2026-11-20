package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One voting member's answer to one written resolution; empty until they answer. A member with a user account answers for themselves; for one without, the secretary records the answer and how it was received. */
@Entity
@Table(name = "legalcompliance_written_responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WrittenResponse extends AggregateRoot<WrittenResponse> {

    @Column(name = "resolution_id", nullable = false) private UUID resolutionId;
    @Column(name = "member_id", nullable = false) private UUID memberId;
    @Column(name = "member_name", nullable = false, length = 255) private String memberName;
    @Column(name = "user_id") private UUID userId;
    @Enumerated(EnumType.STRING) @Column(length = 7) private WrittenChoice choice;
    @Column(length = 500) private String reason;
    @Column(name = "responded_at") private Instant respondedAt;
    @Column(name = "recorded_by_name", length = 255) private String recordedByName;
    @Column(name = "how_received", length = 500) private String howReceived;

    public static WrittenResponse assign(TenantId t, UUID resolutionId, UUID memberId, String memberName, UUID userId) {
        WrittenResponse r = new WrittenResponse();
        r.initTenantId(t);
        r.resolutionId = resolutionId;
        r.memberId = memberId;
        r.memberName = memberName;
        r.userId = userId;
        return r;
    }

    public boolean isPending() { return choice == null; }
    public boolean hasAccount() { return userId != null; }

    /** The member's own answer. Can be changed until the resolution closes. */
    public void respond(WrittenChoice choice, String reason) {
        if (choice == null) throw new IllegalArgumentException("Choose for, against or abstain");
        this.choice = choice;
        this.reason = reason == null || reason.isBlank() ? null : reason.trim();
        this.respondedAt = Instant.now();
        this.recordedByName = null;
        this.howReceived = null;
    }

    /** Recorded by the secretary for a member who has no user account. */
    public void recordOnBehalf(WrittenChoice choice, String reason, String howReceived, String byName) {
        if (hasAccount()) throw new IllegalStateException(memberName + " has a user account and must answer for themselves");
        if (howReceived == null || howReceived.trim().length() < 10) throw new IllegalArgumentException("Say how the answer was received, e.g. the email and its date (at least 10 characters)");
        if (byName == null || byName.isBlank()) throw new IllegalArgumentException("Who is recording this?");
        respond(choice, reason);
        this.recordedByName = byName.trim();
        this.howReceived = howReceived.trim();
    }
}
