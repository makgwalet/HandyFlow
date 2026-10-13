package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.application.internal.WorkItemService.WorkItemView;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.PriorityBand;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItem;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemDisposition;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemStatus;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Queue row: the work item plus the case snapshot the UI needs (account, balance, status) and WHY it is ranked where it is. */
public record WorkItemResponse(
        UUID id,
        UUID caseId,
        String caseNumber,
        String debtorName,
        CaseStatus caseStatus,
        BigDecimal totalOutstanding,
        WorkItemType type,
        WorkItemStatus status,
        String title,
        LocalDate dueDate,
        int priorityScore,
        Integer priorityOverride,
        String priorityOverrideReason,
        int effectivePriority,
        PriorityBand band,
        String priorityReasons,
        UUID assignedToUserId,
        String assignedToUserName,
        LocalDate snoozedUntil,
        String snoozeReason,
        WorkItemDisposition disposition,
        Set<WorkItemDisposition> allowedDispositions,
        Instant completedAt,
        Instant createdAt
) {
    public static WorkItemResponse of(WorkItemView v) {
        WorkItem w = v.item();
        DebtCollectionCase c = v.debtCase();
        return new WorkItemResponse(
                w.getId(), w.getCaseId(), c != null ? c.getCaseNumber() : null, c != null ? c.getDebtorName() : null,
                c != null ? c.getStatus() : null, c != null ? c.getTotalOutstanding() : null, w.getType(),
                w.getStatus(), w.getTitle(), w.getDueDate(), w.getPriorityScore(), w.getPriorityOverride(),
                w.getPriorityOverrideReason(), w.effectivePriority(), PriorityBand.of(w.effectivePriority()),
                w.getPriorityReasons(), w.getAssignedToUserId(), w.getAssignedToUserName(), w.getSnoozedUntil(),
                w.getSnoozeReason(), w.getDisposition(), w.getType().allowedDispositions(), w.getCompletedAt(),
                w.getCreatedAt());
    }
}
