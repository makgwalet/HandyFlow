package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * An external recipient and what they may do. documentIds limits them to some of the documents on the
 * transmittal; empty or missing means all of them. View is always allowed; download and acknowledge
 * are separate permissions (both default to allowed).
 */
public record TransmittalRecipientRequest(
        @NotBlank(message = "Recipient name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters") String name,

        @Size(max = 255, message = "Organisation must not exceed 255 characters") String organisation,

        @NotBlank(message = "Recipient email is required")
        @Email(message = "Recipient email is not valid")
        @Size(max = 255, message = "Email must not exceed 255 characters") String email,

        Boolean canDownload,
        Boolean canAcknowledge,

        @Size(max = 100, message = "At most 100 documents") List<@NotNull UUID> documentIds
) {}
