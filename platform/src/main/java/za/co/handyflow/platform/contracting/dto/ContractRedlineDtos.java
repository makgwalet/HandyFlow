package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;
import za.co.handyflow.platform.contracting.domain.model.BodyDiff;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Versions, comparisons and change proposals for contract bodies. */
public final class ContractRedlineDtos {
    private ContractRedlineDtos() {}

    public record VersionInfo(int versionNo, String source, String note, UUID createdBy, Instant createdAt) {}

    public record VersionBody(int versionNo, String source, String note, Instant createdAt, String body) {}

    /** {@code segments} alternate unchanged blocks and hunks (removed and added blocks). */
    public record DiffResponse(int fromVersion, int toVersion, int hunks, List<BodyDiff.Segment> segments) {}

    public record ProposalRequest(@NotBlank String body, String note) {}

    public record ApplyRequest(List<Integer> acceptedHunks, String note) {}

    public record RejectRequest(@NotBlank String reason) {}

    /** {@code stale} is true when the contract has changed since the proposal was made; a stale proposal cannot be applied. */
    public record ProposalView(UUID id, UUID contractId, int baseVersionNo, String status, String note, UUID proposedBy, Instant createdAt,
                               Instant decidedAt, String decisionNote, String acceptedHunks, Integer appliedVersionNo, boolean stale,
                               int hunks, List<BodyDiff.Segment> segments) {}
}
