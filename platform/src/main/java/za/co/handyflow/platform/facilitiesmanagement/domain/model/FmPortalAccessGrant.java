package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Direct mirror of the confirmed-working portal-grant shape used by
 * every sibling provider module this engagement (payrollbureau.
 * PayPortalAccessGrant, recruitmentagency.RecPortalAccessGrant,
 * bookingagency.BookPortalAccessGrant, accountant.AccPortalAccessGrant,
 * trainingprovider.TrainProvPortalAccessGrant). shared.PortalUser is the
 * genuinely shared login identity; which client a person can see is
 * owned per module, exactly like every one of those.
 */
@Entity(name = "FmPortalAccessGrant")
@Table(name = "fm_portal_access_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmPortalAccessGrant {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "portal_user_id") private UUID portalUserId;
    @Column(name = "invite_email", nullable = false) private String inviteEmail;

    @Column(nullable = false) private String status = "PENDING"; // PENDING, ACTIVE, REVOKED

    @Column(name = "invite_token", unique = true) private String inviteToken;
    @Column(name = "invite_token_expires_at") private Instant inviteTokenExpiresAt;

    @Column(name = "invited_by") private UUID invitedBy;
    @Column(name = "invited_at", nullable = false, updatable = false) private Instant invitedAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "revoked_by") private UUID revokedBy;
    @Column(name = "revoked_at") private Instant revokedAt;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static FmPortalAccessGrant createInvite(UUID tenantId, UUID clientId,
                                                    String inviteEmail, UUID invitedBy) {
        FmPortalAccessGrant g = new FmPortalAccessGrant();
        g.tenantId = tenantId;
        g.clientId = clientId;
        g.inviteEmail = inviteEmail.toLowerCase().trim();
        g.invitedBy = invitedBy;
        g.invitedAt = Instant.now();
        g.status = "PENDING";
        g.inviteToken = generateToken();
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

    public boolean isActive() { return "ACTIVE".equals(status); }
}
