package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalcompliance.application.internal.VotingRules.Tally;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/** The voting record of one decision. OPEN while votes can still be recorded; CLOSED once counted, after which nothing about it changes. */
@Entity
@Table(name = "legalcompliance_resolution_ballots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResolutionBallot extends AggregateRoot<ResolutionBallot> {

    @Column(name = "resolution_id", nullable = false) private UUID resolutionId;
    @Column(name = "meeting_id", nullable = false) private UUID meetingId;
    @Column(name = "committee_id", nullable = false) private UUID committeeId;
    @Enumerated(EnumType.STRING) @Column(name = "resolution_type", nullable = false, length = 10) private ResolutionType resolutionType = ResolutionType.ORDINARY;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 6) private BallotStatus status = BallotStatus.OPEN;
    @Enumerated(EnumType.STRING) @Column(length = 11) private BallotOutcome outcome;
    @Column(name = "votes_for") private Integer votesFor;
    @Column(name = "votes_against") private Integer votesAgainst;
    private Integer abstained;
    private Integer recused;
    @Column(name = "closed_on") private LocalDate closedOn;
    @Column(name = "closed_by_name", length = 255) private String closedByName;
    @Enumerated(EnumType.STRING) @Column(name = "casting_vote", length = 7) private VoteChoice castingVote;
    @Column(name = "casting_by_name", length = 255) private String castingByName;
    @Column(name = "casting_reason", length = 500) private String castingReason;

    public static ResolutionBallot open(TenantId t, UUID resolutionId, UUID meetingId, UUID committeeId) {
        ResolutionBallot b = new ResolutionBallot();
        b.initTenantId(t);
        b.resolutionId = resolutionId;
        b.meetingId = meetingId;
        b.committeeId = committeeId;
        return b;
    }

    public boolean isOpen() { return status == BallotStatus.OPEN; }

    public void setType(ResolutionType type) {
        if (!isOpen()) throw new IllegalStateException("Voting is closed, so the type of resolution cannot change");
        if (type == null) throw new IllegalArgumentException("Choose ordinary or special");
        this.resolutionType = type;
    }

    public void close(BallotOutcome outcome, Tally tally, LocalDate today, String byName) {
        if (!isOpen()) throw new IllegalStateException("Voting is already closed");
        this.outcome = outcome;
        this.votesFor = tally.votesFor();
        this.votesAgainst = tally.against();
        this.abstained = tally.abstained();
        this.recused = tally.recused();
        this.closedOn = today;
        this.closedByName = byName;
        this.status = BallotStatus.CLOSED;
    }

    public boolean hasCastingVote() { return castingVote != null; }

    /** Closes a tied ballot on the chair's casting vote. The counts stay as voted; the casting vote is kept beside them with the chair's name and the reason it was used. */
    public void closeWithCastingVote(BallotOutcome outcome, Tally tally, VoteChoice choice, String chairName, String reason, LocalDate today, String byName) {
        if (choice != VoteChoice.FOR && choice != VoteChoice.AGAINST) throw new IllegalArgumentException("A casting vote must be for or against");
        if (chairName == null || chairName.isBlank()) throw new IllegalArgumentException("Say who the chair is");
        if (reason == null || reason.trim().length() < 10) throw new IllegalArgumentException("Say what allows the casting vote (at least 10 characters)");
        close(outcome, tally, today, byName);
        this.castingVote = choice;
        this.castingByName = chairName.trim();
        this.castingReason = reason.trim();
    }
}
