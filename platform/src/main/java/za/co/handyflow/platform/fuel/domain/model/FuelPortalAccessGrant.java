package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Lets one customer contact see one fuel customer account in the portal. Invite, accept (single use), revoke. */
@Entity(name = "FuelPortalAccessGrant")
@Table(name = "fuel_portal_access_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FuelPortalAccessGrant {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "portal_user_id") private UUID portalUserId;
    @Column(name = "invite_email", nullable = false) private String inviteEmail;
    @Column(name = "status", nullable = false) private String status = "PENDING"; // PENDING | ACTIVE | REVOKED
    @Column(name = "invite_token", unique = true) private String inviteToken;
    @Column(name = "invite_token_expires_at") private Instant inviteTokenExpiresAt;
    @Column(name = "invited_by") private UUID invitedBy;
    @Column(name = "invited_at", nullable = false, updatable = false) private Instant invitedAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "revoked_by") private UUID revokedBy;
    @Column(name = "revoked_at") private Instant revokedAt;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static FuelPortalAccessGrant createInvite(UUID tenantId, UUID accountId, String email, UUID invitedBy) {
        FuelPortalAccessGrant g = new FuelPortalAccessGrant();
        g.tenantId = tenantId; g.accountId = accountId; g.inviteEmail = email.toLowerCase().trim();
        g.invitedBy = invitedBy; g.invitedAt = Instant.now(); g.status = "PENDING";
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        g.inviteToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        g.inviteTokenExpiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60);
        return g;
    }

    public boolean isInviteValid() {
        return "PENDING".equals(status) && inviteTokenExpiresAt != null && Instant.now().isBefore(inviteTokenExpiresAt);
    }

    public void acceptInvite(UUID portalUserId) {
        if (!isInviteValid()) throw new IllegalStateException("This invite has expired or has already been used");
        this.portalUserId = portalUserId; this.status = "ACTIVE"; this.acceptedAt = Instant.now(); this.inviteToken = null;
    }

    public void revoke(UUID by) {
        this.status = "REVOKED"; this.revokedBy = by; this.revokedAt = Instant.now(); this.inviteToken = null;
    }
}
