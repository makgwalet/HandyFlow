package za.co.handyflow.platform.secureaccess.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered external collaborator (consultant, contractor, client contact) who works with the tenant
 * repeatedly. They have ONE standing link instead of a new link for every transmittal. It opens a
 * workspace that lists only the shares issued to their email address (each with its own
 * document assignment and expiry) and nothing else: nothing else.
 *
 * Same protections as a single share link: only hashes are stored, the link alone is not enough
 * (an emailed one-time code starts a short session), and the account can be disabled at once.
 * Disabling is per tenant: the same person registered by another tenant is a separate account.
 */
@Entity
@Table(name = "sda_collaborators")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecureCollaborator {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)      private UUID tenantId;
    @Column(name = "name", nullable = false)           private String name;
    @Column(name = "organisation")                     private String organisation;
    @Column(name = "email", nullable = false)          private String email;
    @Column(name = "token_hash", nullable = false)     private String tokenHash;
    @Column(name = "expires_at", nullable = false)     private Instant expiresAt;
    @Column(name = "disabled_at")                      private Instant disabledAt;
    @Column(name = "disabled_by_name")                 private String disabledByName;
    @Column(name = "disabled_reason", length = 500)    private String disabledReason;
    @Column(name = "otp_hash")                         private String otpHash;
    @Column(name = "otp_expires_at")                   private Instant otpExpiresAt;
    @Column(name = "otp_attempts", nullable = false)   private int otpAttempts;
    @Column(name = "otp_last_sent_at")                 private Instant otpLastSentAt;
    @Column(name = "session_hash")                     private String sessionHash;
    @Column(name = "session_expires_at")               private Instant sessionExpiresAt;
    @Column(name = "last_login_at")                    private Instant lastLoginAt;
    @Column(name = "email_sent_at")                    private Instant emailSentAt;
    @Column(name = "created_by")                       private UUID createdBy;
    @Column(name = "created_by_name")                  private String createdByName;
    @Column(name = "created_at", nullable = false)     private Instant createdAt;

    public static SecureCollaborator create(UUID tenantId, String name, String organisation, String email,
                                             String tokenHash, Instant expiresAt, UUID createdBy, String createdByName) {
        SecureCollaborator c = new SecureCollaborator();
        c.id            = UUID.randomUUID();
        c.tenantId      = tenantId;
        c.name          = name;
        c.organisation  = organisation;
        c.email         = email;
        c.tokenHash     = tokenHash;
        c.expiresAt     = expiresAt;
        c.createdBy     = createdBy;
        c.createdByName = createdByName;
        c.createdAt     = Instant.now();
        return c;
    }

    public boolean isDisabled() { return disabledAt != null; }

    public void rotateToken(String newHash, Instant newExpiresAt) {
        this.tokenHash = newHash;
        this.expiresAt = newExpiresAt;
        clearOtp();
        clearSession();
    }

    public void issueOtp(String hash, Instant now, Instant validUntil) {
        this.otpHash       = hash;
        this.otpExpiresAt  = validUntil;
        this.otpAttempts   = 0;
        this.otpLastSentAt = now;
    }

    public void registerOtpFailure() { this.otpAttempts++; }

    public void clearOtp() {
        this.otpHash      = null;
        this.otpExpiresAt = null;
        this.otpAttempts  = 0;
    }

    public void startSession(String hash, Instant validUntil, Instant now) {
        this.sessionHash      = hash;
        this.sessionExpiresAt = validUntil;
        this.lastLoginAt      = now;
        clearOtp();
    }

    public void clearSession() {
        this.sessionHash      = null;
        this.sessionExpiresAt = null;
    }

    public void markEmailSent(Instant now) { this.emailSentAt = now; }

    public void disable(String byName, String reason, Instant now) {
        if (disabledAt != null) return;                    // keep the first record
        this.disabledAt     = now;
        this.disabledByName = byName;
        this.disabledReason = reason;
        clearOtp();
        clearSession();
    }

    /** Re-enabling needs a new link: the old one may have been shared while the account was off. */
    public void enable(String newHash, Instant newExpiresAt) {
        this.disabledAt = null;
        this.disabledByName = null;
        this.disabledReason = null;
        rotateToken(newHash, newExpiresAt);
    }
}
