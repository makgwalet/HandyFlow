package za.co.handyflow.platform.auditor.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Entity(name = "AuditorAccessGrant")
@Table(name = "auditor_access_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditorAccessGrant {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "portal_user_id") private UUID portalUserId;
    @Column(name = "invite_email", nullable = false) private String inviteEmail;

    @Column(name = "status", nullable = false) private String status = "PENDING";

    @Column(name = "invite_token", unique = true) private String inviteToken;
    @Column(name = "invite_token_expires_at") private Instant inviteTokenExpiresAt;

    @Column(name = "invited_by") private UUID invitedBy;
    @Column(name = "invited_at", nullable = false, updatable = false) private Instant invitedAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "revoked_by") private UUID revokedBy;
    @Column(name = "revoked_at") private Instant revokedAt;

    /** After this moment the grant gives no access, whatever its status. */
    @Column(name = "expires_at") private Instant expiresAt;
    /** What the auditor was engaged to look at, in the owner's words. Shown in the invite screen. */
    @Column(name = "scope_note", columnDefinition = "TEXT") private String scopeNote;

    /** The external audit this access belongs to. Empty for access granted before engagements existed. */
    @Column(name = "engagement_id") private UUID engagementId;

    public static final int DEFAULT_GRANT_DAYS = 90;
    public static final int MAX_GRANT_DAYS = 366;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static AuditorAccessGrant createInvite(UUID tenantId, String inviteEmail, UUID invitedBy) {
        return createInvite(tenantId, inviteEmail, invitedBy, null, null);
    }

    /** expiresAt null means the default period from now. */
    public static AuditorAccessGrant createInvite(UUID tenantId, String inviteEmail, UUID invitedBy, Instant expiresAt, String scopeNote) {
        AuditorAccessGrant g = new AuditorAccessGrant();
        g.tenantId = tenantId;
        g.inviteEmail = inviteEmail.toLowerCase().trim();
        g.invitedBy = invitedBy;
        g.invitedAt = Instant.now();
        g.status = "PENDING";
        g.inviteToken = generateToken();
        g.inviteTokenExpiresAt = Instant.now().plusSeconds(7L * 24 * 60 * 60);
        g.expiresAt = expiresAt != null ? expiresAt : Instant.now().plus(java.time.Duration.ofDays(DEFAULT_GRANT_DAYS));
        g.scopeNote = scopeNote == null || scopeNote.isBlank() ? null : scopeNote.trim();
        return g;
    }

    public void attachToEngagement(UUID engagementId) { this.engagementId = engagementId; }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean isInviteValid() {
        return "PENDING".equals(status)
                && inviteTokenExpiresAt != null
                && Instant.now().isBefore(inviteTokenExpiresAt)
                && (expiresAt == null || Instant.now().isBefore(expiresAt));
    }

    public void acceptInvite(UUID portalUserId) {
        if (!isInviteValid()) {
            throw new IllegalStateException("This invite has expired or has already been used");
        }
        this.portalUserId = portalUserId;
        this.status = "ACTIVE";
        this.acceptedAt = Instant.now();
        this.inviteToken = null;
        this.inviteTokenExpiresAt = null;
    }

    public void revoke(UUID revokedBy) {
        this.status = "REVOKED";
        this.revokedBy = revokedBy;
        this.revokedAt = Instant.now();
        this.inviteToken = null;
        this.inviteTokenExpiresAt = null;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /** Accepted, not revoked, and not past its expiry date. This is the test every portal call must pass. */
    public boolean isUsableAt(Instant now) {
        return isActive() && (expiresAt == null || now.isBefore(expiresAt));
    }

    /** Move the expiry. Only a live grant can be re-dated; a revoked one must be re-invited. */
    public void changeExpiry(Instant newExpiry) {
        if ("REVOKED".equals(status)) throw new IllegalStateException("This access has been revoked; invite the auditor again");
        this.expiresAt = newExpiry;
    }
}