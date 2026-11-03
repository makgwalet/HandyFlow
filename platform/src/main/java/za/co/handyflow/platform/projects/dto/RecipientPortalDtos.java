package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response shapes of the public, per-recipient transmittal page. */
public final class RecipientPortalDtos {

    private RecipientPortalDtos() {}

    public record OtpRequested(String sentTo, int validMinutes) {}

    public record VerifyOtpRequest(
            @NotBlank(message = "Enter the six-digit code")
            @Pattern(regexp = "^[0-9]{6}$", message = "The code is six digits") String code) {}

    /** The session secret goes back in the X-Recipient-Session header on every later call. */
    public record SessionGranted(String session, Instant expiresAt) {}

    public record AcknowledgeRequest(@Size(max = 500, message = "Note must not exceed 500 characters") String note) {}

    public record Document(UUID documentId, String documentNumber, String title, String revision,
                           String statusAtIssue, String fileName, String fileSha256, boolean downloadable) {}

    public record View(String transmittalNumber, String subject, String projectName, String purpose, String notes,
                       String sentBy, Instant sentAt, String recipientName, Instant expiresAt,
                       boolean canDownload, boolean canAcknowledge, Instant acknowledgedAt,
                       List<Document> documents) {}
}
