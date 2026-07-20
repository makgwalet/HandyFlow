package za.co.handyflow.platform.accountant.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FicaDocumentResponse(
        UUID id,
        String docType,
        String fileName,
        String contentType,
        Long fileSizeBytes,
        boolean verified,
        Instant verifiedAt,
        LocalDate expiryDate,
        String uploadedByName,
        // NEW: closes the ambiguity flagged when scoping client-portal
        // upload — STAFF or PORTAL_USER, so this is actually visible to
        // whoever's looking, not just recorded and never surfaced.
        String uploadedByType,
        Instant createdAt
) {
}