// security/dto/GuardDocumentResponse.java
package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record GuardDocumentResponse(
        UUID    id,
        UUID    guardId,
        String  category,
        String  fileUrl,
        String  fileName,
        String  notes,
        UUID    uploadedBy,
        Instant createdAt
) {}