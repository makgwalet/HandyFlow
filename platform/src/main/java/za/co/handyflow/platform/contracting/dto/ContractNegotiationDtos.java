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

    /** NOT_STARTED is returned as {@code negotiation == null}. */
    public record StaffView(NegotiationView negotiation, int currentVersionNo, String body, List<ProposalView> proposals, List<CommentView> comments, List<EventView> events) {}

    /** What one counterparty sees: the current text, their own proposals and the comments addressed to them. */
    public record CounterpartyView(String status, LocalDate responseDeadline, boolean canPropose, int currentVersionNo, String body, List<ProposalView> proposals,
                                   List<CommentView> comments) {}
}
