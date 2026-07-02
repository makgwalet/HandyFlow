package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /snags/{snagId}/photos.
 *
 * Photo bytes are NOT sent to this endpoint.  The client uploads the image
 * directly to object storage (S3 / GCS) via a pre-signed URL, then calls
 * this endpoint with the resulting permanent URL to attach it to the snag.
 *
 * The URL is appended to snag_items.photo_urls (PostgreSQL TEXT[] column)
 * via SnagItem.addPhoto(url).
 *
 * WHY A DEDICATED RECORD RATHER THAN Map&lt;String, String&gt;:
 * Same reason as UpdateBudgetLineRequest — type safety, Bean Validation,
 * and a self-documenting OpenAPI schema instead of an opaque "object".
 */
public record AddPhotoRequest(

        /**
         * Permanent URL of the uploaded photo.
         * Must be a non-blank string; URL format validation is intentionally
         * lenient here because storage providers use varied URL patterns
         * (S3 path-style, virtual-hosted, CDN URLs, etc.).
         */
        @NotBlank(message = "Photo URL is required")
        @Size(max = 1000, message = "URL must be 1000 characters or fewer")
        String url
) {
}