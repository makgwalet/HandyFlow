package za.co.handyflow.platform.creative.dto;

import java.time.Instant;
import java.util.UUID;

public record ApproverResponse(
        UUID    id,
        String  approverName,
        String  approverEmail,
        int     approvalOrder,
        String  status,          // PENDING | APPROVED | REJECTED
        Instant sentAt,
        Instant approvedAt,
        String  rejectionReason
) {}
