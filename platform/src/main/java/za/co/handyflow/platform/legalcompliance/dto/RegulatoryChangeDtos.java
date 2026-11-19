package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.legalcompliance.domain.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response bodies for regulatory change management. */
public final class RegulatoryChangeDtos {
    private RegulatoryChangeDtos() {}

    public record ChangeResponse(
            UUID id, UUID instrumentId, String instrumentTitle, String fromLabel, String toLabel, ChangeKind changeKind,
            ChangeStatus status, String summary, int addedCount, int changedCount, int removedCount, int carriedCount,
            String createdByName, Instant createdAt, Instant closedAt) {
        public static ChangeResponse of(RegulatoryChange c) {
            return new ChangeResponse(c.getId(), c.getInstrumentId(), c.getInstrumentTitle(), c.getFromLabel(), c.getToLabel(),
                    c.getChangeKind(), c.getStatus(), c.getSummary(), c.getAddedCount(), c.getChangedCount(), c.getRemovedCount(),
                    c.getCarriedCount(), c.getCreatedByName(), c.getCreatedAt(), c.getClosedAt());
        }
    }

    public record ImpactResponse(
            UUID id, UUID changeId, String instrumentTitle, String fromLabel, String toLabel, ImpactType impactType,
            String reference, String title, String diffSummary, ImpactStatus status, ImpactOutcome outcome,
            String resolutionNote, String resolvedByName, Instant resolvedAt, LocalDate dueDate, boolean overdue,
            UUID obligationId, UUID oldRequirementId, UUID newRequirementId) {
        public static ImpactResponse of(RegulatoryImpact i, RegulatoryChange c, LocalDate today) {
            return new ImpactResponse(i.getId(), i.getChangeId(), c.getInstrumentTitle(), c.getFromLabel(), c.getToLabel(),
                    i.getImpactType(), i.getReference(), i.getTitle(), i.getDiffSummary(), i.getStatus(), i.getOutcome(),
                    i.getResolutionNote(), i.getResolvedByName(), i.getResolvedAt(), i.getDueDate(),
                    i.isPending() && i.getDueDate().isBefore(today), i.getObligationId(), i.getOldRequirementId(), i.getNewRequirementId());
        }
    }

    public record ChangeDetailResponse(ChangeResponse change, List<ImpactResponse> impacts) {}

    /** justification: 15+ characters for every action except ACKNOWLEDGE. updateObligation: refresh the obligation's wording from the new text. */
    public record ResolveRequest(@NotNull ImpactAction action, String justification, boolean updateObligation) {}

    public record SummaryResponse(long pending, long overdue, long dueWithin7Days, long openChanges) {}
}
