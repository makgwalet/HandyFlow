package za.co.handyflow.platform.approvals.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * FIX: backlog 1.1 (Creative migration) — added publicToken. Missed
 * entirely in the original build (AP never needed it, EXTERNAL_CONTACT
 * was schema-ready but unexercised) — without it, a calling module has
 * no way to retrieve the token it needs to actually build the approval
 * link it sends out. Only meaningful for EXTERNAL_CONTACT steps; null
 * for USER/ROLE responses is harmless (nothing reads it there).
 */
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
        String comment,
        String publicToken
) {}