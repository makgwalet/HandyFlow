package za.co.handyflow.platform.approvals.dto;

import java.time.Instant;
import java.util.UUID;

public record ApprovalStepResponse(
        UUID id,
        UUID approvalRequestId,
        int stepOrder,
        String approverType,
        String approverValue,
        String approverName,
        String status,
        UUID actedBy,
        Instant actedAt,
        String comment
) {}