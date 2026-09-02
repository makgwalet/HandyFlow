package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * This module's own dedicated portal-grant table — never shared with
 * another module's grants, matching every sibling provider module's
 * own convention (RecPortalAccessGrant, BookPortalAccessGrant,
 * PayPortalAccessGrant, AccPortalAccessGrant, WhsePortalAccessGrant,
 * CollAgencyPortalAccessGrant). Login identity itself
 * ({@code shared.PortalUser}) is genuinely shared and portal-type-
 * agnostic; only the grant — "this PortalUser may act on behalf of
 * this TrainProvClient" — is module-specific.
 */
@Entity
@Table(name = "trainprov_portal_access_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvPortalAccessGrant {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "invite_email", nullable = false)
    private String inviteEmail;

    @Column(name = "invite_token", nullable = false)
    private String inviteToken;

    @Column(name = "invite_expires_at", nullable = false)
    private Instant inviteExpiresAt;

    @Column(name = "portal_user_id")
    private UUID portalUserId;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** PENDING | ACCEPTED | REVOKED */
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static TrainProvPortalAccessGrant create(TenantId tenantId, UUID clientId, String inviteEmail,
                                                      String inviteToken, Instant inviteExpiresAt) {
        TrainProvPortalAccessGrant g = new TrainProvPortalAccessGrant();
        g.tenantId = tenantId.getValue();
        g.clientId = clientId;
        g.inviteEmail = inviteEmail.toLowerCase().trim();
        g.inviteToken = inviteToken;
        g.inviteExpiresAt = inviteExpiresAt;
        g.status = "PENDING";
        g.createdAt = Instant.now();
        g.updatedAt = Instant.now();
        return g;
    }

    public boolean isInviteValid() {
        return "PENDING".equals(this.status) && Instant.now().isBefore(this.inviteExpiresAt);
    }

    public void acceptInvite(UUID portalUserId) {
        if (!isInviteValid()) throw new IllegalStateException("Invite is no longer valid");
        this.portalUserId = portalUserId;
        this.acceptedAt = Instant.now();
        this.status = "ACCEPTED";
        this.updatedAt = Instant.now();
    }

    public void revoke() {
        this.status = "REVOKED";
        this.updatedAt = Instant.now();
    }
}
