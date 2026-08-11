// security/dto/SendPortalLinkRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * On-demand portal link send. recipientEmail is supplied at send time
 * rather than stored on Site -- Site has no contactEmail field (confirmed
 * against the actual entity), only contactName/contactPhone. Adding one
 * would be a natural follow-up if a default/remembered recipient is wanted
 * later, but this keeps the current schema unchanged.
 */
public record SendPortalLinkRequest(
        @NotBlank @Email String recipientEmail,
        String customMessage
) {}