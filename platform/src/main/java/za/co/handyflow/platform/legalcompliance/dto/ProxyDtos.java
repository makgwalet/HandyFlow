package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.ProxyStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.VoteChoice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Committee proxies: one member voting for an absent member at one meeting. */
public final class ProxyDtos {

    private ProxyDtos() {}

    /** charterPermits must be true: the person recording it confirms the charter or standing orders allow proxies, and charterClause says where. */
    public record AppointRequest(@NotNull UUID grantorMemberId, @NotNull UUID holderMemberId, @NotBlank @Size(min = 3, max = 200) String charterClause, boolean charterPermits, @Size(max = 500) String instructions) {}

    public record RevokeRequest(@NotBlank @Size(min = 10, max = 500) String reason) {}

    /** usable is false when the grantor attended (so votes for themselves) or the holder was not present; unusableReason says which. */
    public record ProxyView(UUID id, UUID grantorMemberId, String grantorName, UUID holderMemberId, String holderName, String charterClause, String instructions, ProxyStatus status,
                            boolean usable, String unusableReason, String appointedBy, String revokedReason, String revokedBy, Instant revokedAt, Instant appointedAt) {}

    public record ProxyVoteInput(@NotNull UUID proxyId, @NotNull VoteChoice choice, @Size(max = 500) String reason) {}

    public record ProxyCastRequest(@NotNull @Size(min = 1, max = 20) List<ProxyVoteInput> votes) {}

    public record ProxyVoterView(UUID proxyId, String grantorName, String holderName, boolean usable, String unusableReason, VoteChoice choice, String reason) {}

    /** open is false once voting has closed; canVote is true only when the proxy votes can still be recorded. */
    public record ProxyVotingView(UUID resolutionId, boolean open, boolean canVote, List<ProxyVoterView> voters) {}
}
