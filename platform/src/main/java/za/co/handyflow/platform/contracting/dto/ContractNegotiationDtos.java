package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Requests and views for counterparty negotiation. Views for counterparties are separate records and carry no staff-only fields. */
public final class ContractNegotiationDtos {
    private ContractNegotiationDtos() {}

    public record OpenRequest(LocalDate responseDeadline) {}

    public record DeadlineRequest(LocalDate responseDeadline) {}

    public record DecisionRequest(@Size(max = 2000) String note) {}

    public record RejectRequest(@NotBlank @Size(max = 2000) String reason) {}

    public record CloseRequest(@Size(max = 500) String reason) {}

    public record StaffCommentRequest(@NotBlank @Size(max = 4000) String body, Integer blockIndex, String blockExcerpt, UUID proposalId, UUID parentId,
                                      UUID audiencePartyId, boolean internalOnly) {}

    public record ProposalInput(@NotBlank String kind, Integer blockIndex, String originalHtml, String proposedHtml, @Size(max = 2000) String rationale) {}

    public record CounterpartyCommentInput(@NotBlank @Size(max = 4000) String body, Integer blockIndex, String blockExcerpt, UUID proposalId, UUID parentId) {}

    public record Counts(long pending, long conflicted, long accepted, long applied, long rejected, long withdrawn, long superseded) {}

    public record NegotiationView(UUID id, UUID contractId, String status, LocalDate responseDeadline, boolean deadlinePassed, int baseVersionNo, Integer finalVersionNo,
                                  String finalBodyHash, Instant openedAt, Instant readyAt, Instant closedAt, String closeReason, Counts counts) {}

    public record ProposalView(UUID id, UUID partyId, String partyName, String kind, int baseVersionNo, Integer blockIndex, String originalHtml, String proposedHtml,
                               String rationale, String status, UUID conflictWith, Instant decidedAt, String decisionNote, Integer appliedVersionNo,
                               boolean targetStillPresent, Instant createdAt) {}

    public record CommentView(UUID id, String authorType, UUID partyId, Integer blockIndex, String blockExcerpt, UUID proposalId, UUID parentId, String body,
                              boolean internalOnly, Instant createdAt) {}

    public record EventView(UUID id, String actorType, UUID actorUserId, UUID partyId, String eventType, String detail, Instant createdAt) {}

    /** NOT_STARTED is returned as {@code negotiation == null}. {@code blocks} are the body's blocks exactly as the server splits them; proposals quote them verbatim. */
    public record StaffView(NegotiationView negotiation, int currentVersionNo, String body, List<String> blocks, List<ProposalView> proposals, List<CommentView> comments, List<EventView> events) {}

    /** What one counterparty sees: the current text, their own proposals and the comments addressed to them. */
    public record CounterpartyView(String status, LocalDate responseDeadline, boolean canPropose, int currentVersionNo, String body, List<String> blocks, List<ProposalView> proposals,
                                   List<CommentView> comments) {}

    // ── access ───────────────────────────────────────────────────────────────

    public record IssueLinkRequest(@jakarta.validation.constraints.NotNull UUID partyId) {}

    /** The raw link is returned once, here, and never again. */
    public record IssuedLinkView(UUID linkId, UUID partyId, String url, Instant expiresAt, String deliveryStatus) {}

    public record LinkView(UUID id, UUID partyId, String partyName, Instant expiresAt, Instant revokedAt, String revokedReason, String deliveryStatus,
                           Instant lastAccessedAt, Instant createdAt, boolean active) {}

    /** All a stranger holding the link learns before confirming a code. */
    public record LinkInfo(String contractTitle, String channel, String maskedDestination, Instant expiresAt) {}

    public record OtpSentView(String channel, String maskedDestination) {}

    public record VerifyRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "\\d{6}", message = "The code is 6 digits") String code) {}

    public record SessionView(String sessionToken, Instant expiresAt) {}
}
