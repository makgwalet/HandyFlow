package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Grants a client portal user read access to their own matter status and
 * invoices. REVISED after an initial draft (a fixed-shape, required-
 * {@code portalUserId} entity with no invite concept) was found, while
 * building the service layer, to diverge from this codebase's actual
 * dominant convention: {@code AccPortalAccessGrant} — the direct
 * structural sibling, also client-scoped, in the closest precedent
 * module ({@code accountant}) — and {@code AuditorAccessGrant} both use
 * an invite-token/email/status shape, confirmed by direct source read.
 * That is the real, established pattern this codebase actually uses for
 * "a firm invites their external client to a portal," so this entity now
 * matches it: created PENDING with an invite email and a expiring token
 * (no {@code PortalUser} needs to exist yet), and only linked to a real
 * {@code portalUserId} once the invite is accepted — the firm-invites-
 * first flow, not the reversed client-registers-first workaround the
 * initial draft's service layer was forced into.
 */
@Entity
@Table(name = "lp_portal_access_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpPortalAccessGrant {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "portal_user_id")
    private UUID portalUserId; // null until the invite is accepted

    @Column(name = "invite_email", nullable = false)
    private String inviteEmail;

    @Column(nullable = false, length = 20)
    private String status = "PENDING"; // PENDING | ACTIVE | REVOKED

    @Column(name = "invite_token", unique = true)
    private String inviteToken;

    @Column(name = "invite_token_expires_at")
    private Instant inviteTokenExpiresAt;

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

    public static LpPortalAccessGrant createInvite(TenantId tenantId, UUID clientId, String inviteEmail, UUID invitedBy) {
        LpPortalAccessGrant g = new LpPortalAccessGrant();
        g.tenantId = tenantId;
        g.clientId = clientId;
        g.inviteEmail = inviteEmail.toLowerCase().trim();
        g.invitedBy = invitedBy;
        g.invitedAt = Instant.now();
        g.status = "PENDING";
        g.inviteToken = generateToken();
        g.inviteTokenExpiresAt = Instant.now().plusSeconds(7L * 24 * 60 * 60); // 7 days, matching AccPortalAccessGrant
        return g;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean isInviteValid() {
        return "PENDING".equals(status) && inviteTokenExpiresAt != null && Instant.now().isBefore(inviteTokenExpiresAt);
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
        if ("REVOKED".equals(this.status)) {
            throw new IllegalStateException("Grant already revoked");
        }
        this.status = "REVOKED";
        this.revokedBy = revokedBy;
        this.revokedAt = Instant.now();
        this.inviteToken = null;
        this.inviteTokenExpiresAt = null;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
