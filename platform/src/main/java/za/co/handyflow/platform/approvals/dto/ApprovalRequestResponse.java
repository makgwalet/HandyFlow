package za.co.handyflow.platform.approvals.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApprovalRequestResponse(
        UUID id,
        String module,
        String entityType,
        UUID entityId,
        String status,
        UUID submittedBy,
        Instant submittedAt,
        Instant completedAt,
        UUID resubmittedFromId,
        List<ApprovalStepResponse> steps
) {}