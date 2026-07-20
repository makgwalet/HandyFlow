package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * The actual client-portal isolation mechanism for this module. There
 * is no shared "module access" table anywhere in this platform —
 * accountant module code only ever queries this table, and has no code
 * path that could even theoretically reach another module's grants,
 * because that table doesn't exist in this module's vocabulary. A
 * future module (e.g. Recruiter) would get its own equivalent table
 * with its own FK into its own client/candidate entity, not a shared
 * one.
 * <p>
 * Registration is invite-only, not open sign-up — this is B2B access
 * to a client's own financial data, granted by the firm, not self-
 * asserted. Matches the same token-based, no-prior-login pattern
 * already used by AccEngagementLetter's own signing_token (a schema
 * precedent found before this was designed, not invented fresh).
 * <p>
 * portalUserId is nullable and starts null — a grant is created as
 * PENDING, invited by email, before the person has necessarily
 * registered a PortalUser at all. It gets linked once the invite is
 * accepted, either to a brand new PortalUser or an existing one if
 * they already have portal access to a different client or module.
 */
@Entity(name = "AccountantPortalAccessGrant")
@Table(name = "acc_portal_access_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccPortalAccessGrant {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "portal_user_id") private UUID portalUserId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "invite_email", nullable = false) private String inviteEmail;

    @Column(name = "status", nullable = false) private String status = "PENDING";

    @Column(name = "invite_token", unique = true) private String inviteToken;
    @Column(name = "invite_token_expires_at") private Instant inviteTokenExpiresAt;

    @Column(name = "invited_by") private UUID invitedBy;
    @Column(name = "invited_at", nullable = false, updatable = false) private Instant invitedAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "revoked_by") private UUID revokedBy;
    @Column(name = "revoked_at") private Instant revokedAt;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static AccPortalAccessGrant createInvite(UUID tenantId, UUID clientId,
                                                    String inviteEmail, UUID invitedBy) {
        AccPortalAccessGrant g = new AccPortalAccessGrant();
        g.tenantId    = tenantId;
        g.clientId    = clientId;
        g.inviteEmail = inviteEmail.toLowerCase().trim();
        g.invitedBy   = invitedBy;
        g.invitedAt   = Instant.now();
        g.status      = "PENDING";
        g.inviteToken = generateToken();
        // 7 days — long enough for a client to reasonably act on an
        // invite email, short enough that a stale, unused invite link
        // doesn't stay valid indefinitely.
        g.inviteTokenExpiresAt = Instant.now().plusSeconds(7L * 24 * 60 * 60);
        return g;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean isInviteValid() {
        return "PENDING".equals(status)
                && inviteToken != null
                && inviteTokenExpiresAt != null
                && inviteTokenExpiresAt.isAfter(Instant.now());
    }

    public void acceptInvite(UUID portalUserId) {
        if (!isInviteValid()) {
            throw new IllegalStateException("This invite is no longer valid — it may have expired or already been used");
        }
        this.portalUserId = portalUserId;
        this.status        = "ACTIVE";
        this.acceptedAt     = Instant.now();
        // One-time use — a token that's already been redeemed can't be
        // replayed to re-trigger acceptance or extend access.
        this.inviteToken           = null;
        this.inviteTokenExpiresAt  = null;
    }

    public void revoke(UUID revokedBy) {
        this.status     = "REVOKED";
        this.revokedBy  = revokedBy;
        this.revokedAt  = Instant.now();
        // A revoked grant's invite token (if somehow still pending) is
        // also invalidated — revoking must take effect immediately,
        // not leave a dangling way back in.
        this.inviteToken          = null;
        this.inviteTokenExpiresAt = null;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}