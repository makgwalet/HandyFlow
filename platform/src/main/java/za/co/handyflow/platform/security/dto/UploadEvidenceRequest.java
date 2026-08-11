// security/dto/UploadEvidenceRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Either fileUrl (production — a CDN/S3 presigned URL from a separate upload
 * flow) or fileBase64 (dev mode) should be provided, not both. Same
 * dev-vs-production split as GuardService.updatePhoto() — base64 data URIs
 * are accepted but stored as a "PENDING_UPLOAD" placeholder in dev, with a
 * warning logged, rather than silently growing the DB row.
 */
public record UploadEvidenceRequest(
        @NotBlank String category,   // ID_DOCUMENT | ENGAGEMENT_LETTER | THREAT_INTEL | MEDICAL | OTHER
        String fileUrl,
        String fileBase64,
        String fileName,
        String notes
) {}