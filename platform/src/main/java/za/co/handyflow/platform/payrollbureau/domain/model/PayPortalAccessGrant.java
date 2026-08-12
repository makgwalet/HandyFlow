package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * The payroll bureau's own client-portal isolation mechanism — direct
 * structural mirror of accountant.AccPortalAccessGrant (verified
 * against the real class, including the 7-day invite-token expiry this
 * draft initially missed — corrected here to match exactly). That
 * class's own Javadoc explicitly documents the intended pattern for
 * this situation: "a future module would get its own equivalent table
 * ... not a shared one." Only this module's code ever queries this table.
 * <p>
 * portalUserId links to shared.PortalUser — the login identity itself
 * IS shared, but which clients/tenants a person can see is owned here,
 * per module — same "shared identifier, not shared entity" reasoning
 * as PersonIdentityService.
 * <p>
 * SCOPE NOTE: grants a CLIENT BUSINESS OWNER access to their own bureau
 * invoices/deadlines — the direct analog of accountant's client portal.
 * Does NOT cover individual employees viewing their own payslips — a
 * genuinely different feature (different access shape: one employee to
 * one employee record, not client-owner to whole client) — deliberately
 * not built here.
 */
@Entity(name = "PayrollBureauPortalAccessGrant")
@Table(name = "pay_portal_access_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayPortalAccessGrant {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pay_client_id", nullable = false) private UUID payClientId;
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

    private static final SecureRandom RANDOM = new SecureRandom();

    public static PayPortalAccessGrant createInvite(UUID tenantId, UUID payClientId,
                                                    String inviteEmail, UUID invitedBy) {
        PayPortalAccessGrant g = new PayPortalAccessGrant();
        g.tenantId = tenantId;
        g.payClientId = payClientId;
        g.inviteEmail = inviteEmail.toLowerCase().trim();
        g.invitedBy = invitedBy;
        g.invitedAt = Instant.now();
        g.status = "PENDING";
        g.inviteToken = generateToken();
        g.inviteTokenExpiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60); // 7 days, same window as accountant's
        return g;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean isInviteValid() {
        return "PENDING".equals(status)
                && inviteTokenExpiresAt != null
                && Instant.now().isBefore(inviteTokenExpiresAt);
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