package za.co.handyflow.platform.collectionsagency.domain.model;

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
 * Grants a CREDITOR CLIENT's own staff access to a portal view of their
 * portfolio with this agency (accounts placed, recovery status, trust/
 * remittance statements) — the direct analog of PayPortalAccessGrant/
 * RecPortalAccessGrant. Explicitly its OWN table, not a shared one — same
 * precedent statement already made for those two modules: each provider
 * module gets its own portal-access-grant table, scoped to its own
 * client id column, rather than a shared cross-module grant table.
 */
@Entity(name = "CollAgencyPortalAccessGrant")
@Table(name = "collagency_portal_access_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollAgencyPortalAccessGrant {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
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

    public static CollAgencyPortalAccessGrant createInvite(UUID tenantId, UUID clientId, String inviteEmail,
                                                            UUID invitedBy) {
        CollAgencyPortalAccessGrant g = new CollAgencyPortalAccessGrant();
        g.tenantId = tenantId;
        g.clientId = clientId;
        g.inviteEmail = inviteEmail.toLowerCase().trim();
        g.invitedBy = invitedBy;
        g.invitedAt = Instant.now();
        g.status = "PENDING";
        g.inviteToken = generateToken();
        g.inviteTokenExpiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60); // 7 days, same window as accountant/payrollbureau
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
