package za.co.handyflow.platform.legalcompliance.dto;

import za.co.handyflow.platform.legalcompliance.domain.model.DataCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarRequest;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarRequestType;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DsarRequestResponse(
        UUID id,
        String requestNumber,
        DsarRequestType requestType,
        DataCategory dataCategory,
        String requesterName,
        String requesterEmail,
        String requesterContact,
        LocalDate receivedDate,
        LocalDate dueDate,
        DsarStatus status,
        UUID assignedToUserId,
        String assignedToUserName,
        String resolutionNotes,
        LocalDate completedDate,
        boolean overdue,
        Instant createdAt,
        Instant updatedAt
) {
    public static DsarRequestResponse of(DsarRequest r) {
        return new DsarRequestResponse(
                r.getId(), r.getRequestNumber(), r.getRequestType(), r.getDataCategory(), r.getRequesterName(),
                r.getRequesterEmail(), r.getRequesterContact(), r.getReceivedDate(), r.getDueDate(), r.getStatus(),
                r.getAssignedToUserId(), r.getAssignedToUserName(), r.getResolutionNotes(), r.getCompletedDate(),
                r.isOverdue(LocalDate.now()), r.getCreatedAt(), r.getUpdatedAt());
    }
}
