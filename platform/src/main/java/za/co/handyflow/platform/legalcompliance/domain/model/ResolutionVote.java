package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** One member's vote on one decision. Changed only through the service, and only while the ballot is open. */
@Entity
@Table(name = "legalcompliance_resolution_votes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResolutionVote extends AggregateRoot<ResolutionVote> {

    public static final int MIN_RECUSAL_REASON = 10;

    @Column(name = "resolution_id", nullable = false) private UUID resolutionId;
    @Column(name = "member_id", nullable = false) private UUID memberId;
    @Column(name = "member_name", nullable = false, length = 255) private String memberName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 7) private VoteChoice choice;
    @Column(length = 500) private String reason;
    @Column(name = "recorded_by_name", length = 255) private String recordedByName;

    public static ResolutionVote cast(TenantId t, UUID resolutionId, UUID memberId, String memberName, VoteChoice choice, String reason, String byName) {
        ResolutionVote v = new ResolutionVote();
        v.initTenantId(t);
        v.resolutionId = resolutionId;
        v.memberId = memberId;
        v.memberName = memberName;
        v.change(choice, reason, byName);
        return v;
    }

    public void change(VoteChoice choice, String reason, String byName) {
        if (choice == null) throw new IllegalArgumentException("A vote is required");
        String r = reason == null || reason.isBlank() ? null : reason.trim();
        if (choice == VoteChoice.RECUSED && (r == null || r.length() < MIN_RECUSAL_REASON)) throw new IllegalArgumentException("Say why the member is recused (at least " + MIN_RECUSAL_REASON + " characters)");
        this.choice = choice;
        this.reason = r == null ? null : r.length() > 500 ? r.substring(0, 500) : r;
        this.recordedByName = byName;
    }
}
