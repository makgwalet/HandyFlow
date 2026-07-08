package za.co.handyflow.platform.creative.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProofResponse(
        UUID    id,
        UUID    jobId,
        int     versionNumber,
        String  title,
        String  fileName,
        String  fileType,
        boolean hasFile,
        boolean hasThumbnail,
        String  status,
        String  approvalToken,   // only returned to authenticated team users
        Instant tokenExpiresAt,
        Instant sentAt,
        String  sentToEmail,
        // NEW: the "client has seen it, why haven't they responded" signal
        // the gap analysis flagged as missing — captured on the backend now,
        // exposed here so it's actually visible to staff, not just recorded.
        Instant viewedAt,
        Instant approvedAt,
        String  approvedByName,
        String  rejectionReason,
        String  notes,
        List<CommentResponse> comments,
        // NEW: multi-stakeholder approval — approvalMode is always present
        // (defaults to "SINGLE"); approvers is empty for SINGLE-mode
        // proofs, populated once a staff member has configured multiple
        // reviewers via configureApprovers().
        String  approvalMode,
        List<ApproverResponse> approvers,
        Instant createdAt
) {}