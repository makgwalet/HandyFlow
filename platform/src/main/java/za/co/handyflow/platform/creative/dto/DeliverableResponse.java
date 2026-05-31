package za.co.handyflow.platform.creative.dto;

import java.time.Instant;
import java.util.UUID;

public record DeliverableResponse(
        UUID    id,
        String  fileName,
        String  fileType,
        Long    fileSize,
        String  notes,
        UUID    uploadedBy,
        Instant createdAt
) {}