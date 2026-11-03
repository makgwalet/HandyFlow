package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One external recipient of a transmittal, with their OWN access link.
 *
 * Only hashes are stored: of the link token, of the current one-time code and of the current session.
 * The link itself exists only in the email sent to the recipient. Sending a fresh link (rotateToken)
 * kills the old one and any open session, so a forwarded or leaked link can be shut down at once.
 */
@Entity
@Table(name = "project_transmittal_recipients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTransmittalRecipient {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)      private UUID tenantId;
    @Column(name = "project_id", nullable = false)     private UUID projectId;
    @Column(name = "transmittal_id", nullable = false) private UUID transmittalId;
    @Column(name = "name", nullable = false)           private String name;
    @Column(name = "organisation")                     private String organisation;
    @Column(name = "email", nullable = false)          private String email;
    @Column(name = "token_hash", nullable = false)     private String tokenHash;
    @Column(name = "can_download", nullable = false)   private boolean canDownload;
    @Column(name = "can_acknowledge", nullable = false) private boolean canAcknowledge;
    @Column(name = "expires_at", nullable = false)     private Instant expiresAt;
    @Column(name = "revoked_at")                       private Instant revokedAt;
    @Column(name = "revoked_by_name")                  private String revokedByName;
    @Column(name = "revoke_reason", length = 500)      private String revokeReason;
    @Column(name = "otp_hash")                         private String otpHash;
    @Column(name = "otp_expires_at")                   private Instant otpExpiresAt;
    @Column(name = "otp_attempts", nullable = false)   private int otpAttempts;
    @Column(name = "otp_last_sent_at")                 private Instant otpLastSentAt;
    @Column(name = "session_hash")                     private String sessionHash;
    @Column(name = "session_expires_at")               private Instant sessionExpiresAt;
    @Column(name = "verified_at")                      private Instant verifiedAt;
    @Column(name = "last_access_at")                   private Instant lastAccessAt;
    @Column(name = "email_sent_at")                    private Instant emailSentAt;
    @Column(name = "acknowledged_at")                  private Instant acknowledgedAt;
    @Column(name = "acknowledged_note", length = 500)  private String acknowledgedNote;
    /** Set when this address belongs to a registered collaborator account (see ProjectCollaborator). */
    @Column(name = "collaborator_id")                  private UUID collaboratorId;
    @Column(name = "created_by")                       private UUID createdBy;
    @Column(name = "created_by_name")                  private String createdByName;
    @Column(name = "created_at", nullable = false)     private Instant createdAt;

    public static ProjectTransmittalRecipient create(UUID tenantId, UUID projectId, UUID transmittalId,
                                                     String name, String organisation, String email,
                                                     String tokenHash, boolean canDownload, boolean canAcknowledge,
                                                     Instant expiresAt, UUID createdBy, String createdByName) {
        ProjectTransmittalRecipient r = new ProjectTransmittalRecipient();
        r.id             = UUID.randomUUID();
        r.tenantId       = tenantId;
        r.projectId      = projectId;
        r.transmittalId  = transmittalId;
        r.name           = name;
        r.organisation   = organisation;
        r.email          = email;
        r.tokenHash      = tokenHash;
        r.canDownload    = canDownload;
        r.canAcknowledge = canAcknowledge;
        r.expiresAt      = expiresAt;
        r.createdBy      = createdBy;
        r.createdByName  = createdByName;
        r.createdAt      = Instant.now();
        return r;
    }

    public boolean isRevoked() { return revokedAt != null; }

    public void linkCollaborator(UUID collaboratorId) { this.collaboratorId = collaboratorId; }

    /** New link: the old one stops working, and so does any open session and any unused code. */
    public void rotateToken(String newTokenHash, Instant newExpiresAt) {
        if (revokedAt != null) throw new IllegalStateException("Access for this recipient was revoked");
        this.tokenHash = newTokenHash;
        this.expiresAt = newExpiresAt;
        clearOtp();
        clearSession();
    }

    public void issueOtp(String hash, Instant now, Instant validUntil) {
        this.otpHash        = hash;
        this.otpExpiresAt   = validUntil;
        this.otpAttempts    = 0;
        this.otpLastSentAt  = now;
    }

    public void registerOtpFailure() { this.otpAttempts++; }

    public void clearOtp() {
        this.otpHash      = null;
        this.otpExpiresAt = null;
        this.otpAttempts  = 0;
    }

    public void startSession(String hash, Instant validUntil, Instant now) {
        this.sessionHash       = hash;
        this.sessionExpiresAt  = validUntil;
        this.verifiedAt        = now;
        this.lastAccessAt      = now;
        clearOtp();
    }

    public void clearSession() {
        this.sessionHash      = null;
        this.sessionExpiresAt = null;
    }

    public void touch(Instant now) { this.lastAccessAt = now; }

    public void markEmailSent(Instant now) { this.emailSentAt = now; }

    public void revoke(String byName, String reason, Instant now) {
        if (revokedAt != null) return;                     // already revoked: keep the first record
        this.revokedAt     = now;
        this.revokedByName = byName;
        this.revokeReason  = reason;
        clearOtp();
        clearSession();
    }

    /** Once only; the first acknowledgement stands. */
    public void acknowledge(String note, Instant now) {
        if (acknowledgedAt != null) throw new IllegalStateException("Already acknowledged");
        this.acknowledgedAt   = now;
        this.acknowledgedNote = note;
    }
}
