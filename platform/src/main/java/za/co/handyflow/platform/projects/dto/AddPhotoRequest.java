package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /snags/{snagId}/photos.
 *
 * Photo bytes are NOT sent here. The client uploads to object storage via a
 * pre-signed URL, then registers the resulting permanent URL.
 *
 * CHUNK 2: the URL is later rendered as a link/image in the UI, so executable
 * schemes (javascript:, data:, vbscript:) are rejected to prevent stored XSS.
 * Everything else (https, storage keys, CDN paths) remains allowed because
 * providers use varied URL shapes.
 */
public record AddPhotoRequest(

        @NotBlank(message = "Photo URL is required")
        @Size(max = 1000, message = "URL must be 1000 characters or fewer")
        @Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript):).+$",
                message = "URL scheme is not allowed")
        String url
) {
}