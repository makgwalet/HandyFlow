package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Lets one person see one client's sent quotes, live agreements and issued invoices. The sign-in itself is the shared
 * portal login; this says what that person may see. The invite token is single use and expires.
 */
@Entity(name = "CompliancePortalGrant")
@Table(name = "compliance_portal_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompliancePortalGrant {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "portal_user_id")
    private UUID portalUserId;

    @Column(name = "invite_email", nullable = false)
    private String inviteEmail;

    @Column(nullable = false)
    private String status = PortalRules.PENDING;

    @Column(name = "invite_token")
    private String inviteToken;

    @Column(name = "invite_token_expires_at")
    private Instant inviteTokenExpiresAt;

    @Column(name = "email_sent", nullable = false)
    private boolean emailSent;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "invited_at", nullable = false, updatable = false)
    private Instant invitedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static CompliancePortalGrant invite(UUID tenantId, UUID clientId, String email, UUID by, Instant now) {
        String problem = PortalRules.emailProblem(email);
        if (problem != null) throw new IllegalArgumentException(problem);
        CompliancePortalGrant g = new CompliancePortalGrant();
        g.tenantId = tenantId;
        g.clientId = clientId;
        g.inviteEmail = PortalRules.normaliseEmail(email);
        g.invitedBy = by;
        g.invitedAt = now;
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        g.inviteToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        g.inviteTokenExpiresAt = now.plusSeconds(PortalRules.INVITE_DAYS * 24L * 60 * 60);
        return g;
    }

    public boolean isInviteValid(Instant now) {
        return PortalRules.PENDING.equals(status) && inviteToken != null && inviteTokenExpiresAt != null && inviteTokenExpiresAt.isAfter(now);
    }

    public boolean isActive() { return PortalRules.ACTIVE.equals(status); }

    public void emailWasSent(boolean sent) { this.emailSent = sent; }

    /** Accepting uses the token up, so the same link cannot be used twice. */
    public void accept(UUID portalUserId, Instant now) {
        if (portalUserId == null) throw new IllegalArgumentException("A portal account is required");
        if (!isInviteValid(now)) throw new IllegalStateException("This invite has expired or has already been used");
        this.portalUserId = portalUserId;
        this.status = PortalRules.ACTIVE;
        this.acceptedAt = now;
        this.inviteToken = null;
        this.inviteTokenExpiresAt = null;
    }

    public void revoke(UUID by, Instant now) {
        if (PortalRules.REVOKED.equals(status)) throw new IllegalStateException("Access has already been revoked");
        this.status = PortalRules.REVOKED;
        this.revokedBy = by;
        this.revokedAt = now;
        this.inviteToken = null;
        this.inviteTokenExpiresAt = null;
    }
}
