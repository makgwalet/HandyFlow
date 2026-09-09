package za.co.handyflow.platform.property.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Grants a tenant (lessee) portal access to view their own lease,
 * payment history, and unit inspections -- direct structural mirror of
 * WhsePortalAccessGrant/CollAgencyPortalAccessGrant/PayPortalAccessGrant.
 * Scoped to a lease rather than a client -- Property's tenants are
 * individual lessees identified by Lease.lesseeEmail, not companies.
 * Own dedicated table, same established precedent as every sibling
 * provider module (each gets its own grant table, never a shared
 * cross-module one).
 */
@Entity(name = "PropPortalAccessGrant")
@Table(name = "prop_portal_access_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PropPortalAccessGrant {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "lease_id", nullable = false) private UUID leaseId;
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

    public static PropPortalAccessGrant createInvite(UUID tenantId, UUID leaseId, String inviteEmail,
                                                      UUID invitedBy) {
        PropPortalAccessGrant g = new PropPortalAccessGrant();
        g.tenantId = tenantId;
        g.leaseId = leaseId;
        g.inviteEmail = inviteEmail.toLowerCase().trim();
        g.invitedBy = invitedBy;
        g.invitedAt = Instant.now();
        g.status = "PENDING";
        g.inviteToken = generateToken();
        g.inviteTokenExpiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60); // 7 days, matching every sibling provider module's own invite window
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
        this.inviteToken = null; // single-use
    }

    public void revoke(UUID revokedBy) {
        this.status = "REVOKED";
        this.revokedBy = revokedBy;
        this.revokedAt = Instant.now();
    }
}
