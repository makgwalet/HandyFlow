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
        Instant approvedAt,
        String  approvedByName,
        String  rejectionReason,
        String  notes,
        List<CommentResponse> comments,
        Instant createdAt
) {}