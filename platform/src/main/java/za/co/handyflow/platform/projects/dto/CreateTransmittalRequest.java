package za.co.handyflow.platform.projects.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/**
 * Only live documents (CURRENT, FOR_REVIEW, APPROVED) can be sent — a superseded or voided
 * revision is refused, so nobody is sent something that is no longer valid.
 *
 * The recipient named above and every additional recipient with an email address each get their OWN
 * secure link (valid expiryDays, default 14). Documents are never attached to the email.
 */
public record CreateTransmittalRequest(
        @NotBlank(message = "Subject is required")
        @Size(max = 200, message = "Subject must not exceed 200 characters") String subject,

        @NotBlank(message = "Recipient name is required")
        @Size(max = 255, message = "Recipient name must not exceed 255 characters") String recipientName,

        @Size(max = 255, message = "Organisation must not exceed 255 characters") String recipientOrganisation,

        @Email(message = "Recipient email is not valid")
        @Size(max = 255, message = "Email must not exceed 255 characters") String recipientEmail,

        @NotBlank(message = "Purpose is required")
        @Pattern(regexp = "FOR_INFORMATION|FOR_REVIEW|FOR_APPROVAL|FOR_CONSTRUCTION|AS_BUILT",
                message = "purpose must be one of FOR_INFORMATION, FOR_REVIEW, FOR_APPROVAL, FOR_CONSTRUCTION, AS_BUILT")
        String purpose,

        @Size(max = 2000, message = "Notes must not exceed 2 000 characters") String notes,

        @NotEmpty(message = "Select at least one document")
        @Size(max = 100, message = "At most 100 documents per transmittal")
        List<@NotNull UUID> documentIds,

        @Min(value = 1, message = "Link expiry must be at least 1 day")
        @Max(value = 90, message = "Link expiry must not exceed 90 days") Integer expiryDays,

        @Size(max = 20, message = "At most 20 additional recipients")
        List<@Valid TransmittalRecipientRequest> additionalRecipients
) {
    /** Backwards compatible: the earlier shape without expiry or extra recipients. */
    public CreateTransmittalRequest(String subject, String recipientName, String recipientOrganisation,
                                    String recipientEmail, String purpose, String notes, List<UUID> documentIds) {
        this(subject, recipientName, recipientOrganisation, recipientEmail, purpose, notes, documentIds, null, null);
    }
}