package za.co.handyflow.platform.approvals.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * FIX: backlog 1.1 (Creative migration) — added approvalMode. Missed in
 * the original build; without it, a caller that needs to know whether a
 * request is SEQUENTIAL/PARALLEL_ALL/PARALLEL_ANY_ONE after the fact
 * (e.g. to decide whether "notify the next approver" applies) would
 * have had to guess at it from step-order patterns instead of just
 * reading it. Null for auto-approved requests (no rule/chain ever
 * applied) — matches ApprovalRequest.approvalMode's own nullability.
 */
public record ApprovalRequestResponse(
        UUID id,
        String module,
        String entityType,
        UUID entityId,
        String status,
        String approvalMode,
        UUID submittedBy,
        Instant submittedAt,
        Instant completedAt,
        UUID resubmittedFromId,
        List<ApprovalStepResponse> steps
) {}