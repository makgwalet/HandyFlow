package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.BallotOutcome;
import za.co.handyflow.platform.legalcompliance.domain.model.BallotStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.ResolutionType;
import za.co.handyflow.platform.legalcompliance.domain.model.VoteChoice;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Voting on committee decisions. */
public final class VotingDtos {

    private VotingDtos() {}

    public record VoteInput(@NotNull UUID memberId, @NotNull VoteChoice choice, @Size(max = 500) String reason) {}

    public record CastRequest(@NotNull @Size(min = 1, max = 50) List<VoteInput> votes) {}

    /** charterPermits must be true: the person closing is confirming that the committee's charter or standing orders give the chair a casting vote. */
    public record CastingVoteRequest(@NotNull VoteChoice choice, @NotBlank @Size(min = 10, max = 500) String reason, boolean charterPermits) {}

    public record TypeRequest(@NotNull ResolutionType type) {}

    public record TallyView(int votesFor, int against, int abstained, int recused) {}

    /** eligible is false for members who were present but cannot vote (observers) or were not on the committee on the meeting date. */
    public record VoterView(UUID memberId, String name, String role, boolean eligible, VoteChoice choice, String reason) {}

    public record VotingView(UUID resolutionId, String description, ResolutionType type, String requirement, BallotStatus status, BallotOutcome outcome, LocalDate closedOn, String closedByName,
                             TallyView tally, int quorumMin, boolean canVote, List<VoterView> voters, List<String> problems,
                             boolean tied, String castingChair, VoteChoice castingVote, String castingVoteBy, String castingVoteReason) {}

    public record BallotSummary(UUID resolutionId, ResolutionType type, BallotStatus status, BallotOutcome outcome, TallyView tally) {}
}
