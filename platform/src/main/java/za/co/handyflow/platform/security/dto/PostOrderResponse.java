package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostOrderResponse(
        UUID id, UUID siteId, UUID postId, String postName, int version, String status,
        Instant effectiveFrom, Instant effectiveTo,
        String instructions, String duties, String emergencyProcedures, String restrictedAreas, String accessRules,
        UUID createdBy, UUID publishedBy, Instant publishedAt, Instant createdAt,
        List<SecurityContactResponse> contacts,
        List<PostOrderAttachmentResponse> attachments
) {}
