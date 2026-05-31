package za.co.handyflow.platform.creative.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicProofResponse(
        UUID    id,
        String  jobTitle,
        String  clientName,
        String  tenantName,
        int     versionNumber,
        String  title,
        String  fileUrl,         // base64 — client needs to see the proof
        String  thumbnailUrl,
        String  fileName,
        String  fileType,
        String  status,
        List<CommentResponse> comments,
        Instant createdAt
) {}


