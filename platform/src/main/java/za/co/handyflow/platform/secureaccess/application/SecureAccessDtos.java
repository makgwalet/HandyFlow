package za.co.handyflow.platform.secureaccess.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.secureaccess.domain.model.SecureAccessLog;
import za.co.handyflow.platform.secureaccess.domain.model.SecureCollaborator;
import za.co.handyflow.platform.secureaccess.domain.model.SecureCollaboratorLog;
import za.co.handyflow.platform.secureaccess.domain.model.SecureRecipient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response shapes of the engine: staff-facing (used by modules) and public (recipient pages). */
public final class SecureAccessDtos {

    private SecureAccessDtos() {}

    // ── Opening a share (called by a module) ──────────────────────────────────

    /** One document to issue. The module has already checked it may be issued; the engine freezes these fields. */
    public record ItemSpec(String docModuleCode, String docSourceType, UUID docSourceId, String revision,
                           String displayNumber, String title, String statusAtIssue, String fileName, String fileSha256) {}

    /**
     * An external recipient and what they may do. itemIndexes (positions in the share's item list) limits them to
     * some of the items; empty or null means all of them. View is always allowed.
     */
    public record RecipientSpec(String name, String organisation, String email, Boolean canDownload,
                                Boolean canAcknowledge, List<Integer> itemIndexes) {}

    public record OpenShare(UUID tenantId, String moduleCode, String sourceType, UUID sourceId, UUID contextId,
                            List<ItemSpec> items, List<RecipientSpec> recipients, Integer expiryDays,
                            boolean attachFiles, UUID by, String byName) {}

    public record ShareOpened(UUID shareId, List<RecipientView> recipients) {}

    // ── Staff views (never carry a link or any secret) ────────────────────────

    /** status: ACTIVE, EXPIRED or REVOKED. itemIds empty means every item in the share. */
    public record RecipientView(UUID id, String name, String organisation, String email, boolean canDownload,
                                boolean canAcknowledge, String status, Instant expiresAt, Instant revokedAt,
                                String revokedByName, String revokeReason, Instant emailSentAt, Instant verifiedAt,
                                Instant lastAccessAt, Instant acknowledgedAt, List<UUID> itemIds) {
        public static RecipientView of(SecureRecipient r, List<UUID> scopedItemIds, Instant now) {
            String status = r.isRevoked() ? "REVOKED" : r.getExpiresAt().isAfter(now) ? "ACTIVE" : "EXPIRED";
            return new RecipientView(r.getId(), r.getName(), r.getOrganisation(), r.getEmail(), r.isCanDownload(),
                    r.isCanAcknowledge(), status, r.getExpiresAt(), r.getRevokedAt(), r.getRevokedByName(),
                    r.getRevokeReason(), r.getEmailSentAt(), r.getVerifiedAt(), r.getLastAccessAt(),
                    r.getAcknowledgedAt(), scopedItemIds);
        }
    }

    public record AccessLogView(UUID id, UUID recipientId, UUID documentId, String revision, String action,
                                String detail, String ipAddress, String actorName, Instant occurredAt) {
        public static AccessLogView of(SecureAccessLog l) {
            return new AccessLogView(l.getId(), l.getRecipientId(), l.getDocSourceId(), l.getRevision(), l.getAction(),
                    l.getDetail(), l.getIpAddress(), l.getActorName(), l.getOccurredAt());
        }
    }

    // ── Collaborators ─────────────────────────────────────────────────────────

    public record RegisterCollaboratorRequest(
            @NotBlank(message = "Name is required") @Size(max = 255, message = "Name must not exceed 255 characters") String name,
            @Size(max = 255, message = "Organisation must not exceed 255 characters") String organisation,
            @NotBlank(message = "Email is required") @Email(message = "Email is not valid")
            @Size(max = 255, message = "Email must not exceed 255 characters") String email,
            Integer expiryDays) {}

    public record DisableCollaboratorRequest(
            @NotBlank(message = "A reason is required") @Size(max = 500, message = "Reason must not exceed 500 characters") String reason,
            Boolean revokeLinks) {}

    public record ReissueRequest(Integer expiryDays) {}

    public record RevokeRequest(
            @NotBlank(message = "A reason is required") @Size(max = 500, message = "Reason must not exceed 500 characters") String reason) {}

    /** status: ACTIVE, EXPIRED or DISABLED. */
    public record CollaboratorView(UUID id, String name, String organisation, String email, String status,
                                   Instant expiresAt, Instant disabledAt, String disabledByName, String disabledReason,
                                   Instant emailSentAt, Instant lastLoginAt, Instant createdAt) {
        public static CollaboratorView of(SecureCollaborator c, Instant now) {
            String status = c.isDisabled() ? "DISABLED" : c.getExpiresAt().isAfter(now) ? "ACTIVE" : "EXPIRED";
            return new CollaboratorView(c.getId(), c.getName(), c.getOrganisation(), c.getEmail(), status,
                    c.getExpiresAt(), c.getDisabledAt(), c.getDisabledByName(), c.getDisabledReason(),
                    c.getEmailSentAt(), c.getLastLoginAt(), c.getCreatedAt());
        }
    }

    public record CollaboratorLogEntry(UUID id, String action, String detail, String ipAddress, String actorName,
                                       Instant occurredAt) {
        public static CollaboratorLogEntry of(SecureCollaboratorLog l) {
            return new CollaboratorLogEntry(l.getId(), l.getAction(), l.getDetail(), l.getIpAddress(), l.getActorName(), l.getOccurredAt());
        }
    }

    // ── Public (recipient pages) ──────────────────────────────────────────────

    public record OtpRequested(String sentTo, int validMinutes) {}

    public record VerifyOtpRequest(
            @NotBlank(message = "Enter the six-digit code")
            @Pattern(regexp = "^[0-9]{6}$", message = "The code is six digits") String code) {}

    /** The session secret goes back in the X-Recipient-Session header on every later call. */
    public record SessionGranted(String session, Instant expiresAt) {}

    public record AcknowledgeRequest(@Size(max = 500, message = "Note must not exceed 500 characters") String note) {}

    public record Document(UUID documentId, String documentNumber, String title, String revision, String statusAtIssue,
                           String fileName, String fileSha256, boolean downloadable, String unavailableReason) {}

    public record View(String reference, String subject, String contextName, String purpose, String notes,
                       String sentBy, Instant sentAt, String recipientName, Instant expiresAt,
                       boolean canDownload, boolean canAcknowledge, Instant acknowledgedAt, List<Document> documents) {}

    /** One share issued to a collaborator. recipientId is what the download and acknowledge calls take. */
    public record WorkspaceEntry(@NotNull UUID recipientId, View share) {}

    public record Workspace(String name, String organisation, Instant expiresAt, List<WorkspaceEntry> shares) {}
}
