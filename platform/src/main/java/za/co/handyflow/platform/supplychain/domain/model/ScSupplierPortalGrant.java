package za.co.handyflow.platform.supplychain.domain.model;

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

/** Lets one person at a supplier log in to quote on that company's RFQs. Same lifecycle as every other portal grant. */
@Entity(name = "ScSupplierPortalGrant")
@Table(name = "sc_supplier_portal_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScSupplierPortalGrant {

    public static final String PENDING = "PENDING", ACTIVE = "ACTIVE", REVOKED = "REVOKED";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "supplier_id", nullable = false) private UUID supplierId;
    @Column(name = "portal_user_id") private UUID portalUserId;
    @Column(name = "invite_email", nullable = false) private String inviteEmail;
    @Column(name = "status", nullable = false) private String status = PENDING;
    @Column(name = "invite_token") private String inviteToken;
    @Column(name = "invite_token_expires_at") private Instant inviteTokenExpiresAt;
    @Column(name = "invited_by") private UUID invitedBy;
    @Column(name = "invited_at", nullable = false, updatable = false) private Instant invitedAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "revoked_by") private UUID revokedBy;
    @Column(name = "revoked_at") private Instant revokedAt;

    public static ScSupplierPortalGrant createInvite(UUID tenantId, UUID supplierId, String email, UUID invitedBy) {
        ScSupplierPortalGrant g = new ScSupplierPortalGrant();
        g.tenantId = tenantId;
        g.supplierId = supplierId;
        g.inviteEmail = email.toLowerCase().trim();
        g.invitedBy = invitedBy;
        g.invitedAt = Instant.now();
        g.inviteToken = newToken();
        g.inviteTokenExpiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60);
        return g;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean isInviteValid() {
        return PENDING.equals(status) && inviteTokenExpiresAt != null && Instant.now().isBefore(inviteTokenExpiresAt);
    }

    public void acceptInvite(UUID portalUserId) {
        if (!isInviteValid()) throw new IllegalStateException("This invite has expired or has already been used");
        this.portalUserId = portalUserId;
        this.status = ACTIVE;
        this.acceptedAt = Instant.now();
        this.inviteToken = null; // single use
    }

    /** A fresh link for a pending invite that expired or went missing. */
    public void reissue() {
        if (!PENDING.equals(status)) throw new IllegalStateException("Only a pending invite can be re-sent");
        this.inviteToken = newToken();
        this.inviteTokenExpiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60);
    }

    public void revoke(UUID by) {
        this.status = REVOKED;
        this.revokedBy = by;
        this.revokedAt = Instant.now();
        this.inviteToken = null;
    }

    public boolean isActive() { return ACTIVE.equals(status); }
}
