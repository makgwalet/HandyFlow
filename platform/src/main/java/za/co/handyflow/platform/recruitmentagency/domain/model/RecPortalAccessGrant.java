package za.co.handyflow.platform.recruitmentagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Direct structural mirror of payrollbureau.PayPortalAccessGrant, which
 * is itself confirmed working end-to-end this session (Section 53/54,
 * verified compiling and running). Same reasoning as that class's own
 * Javadoc: only this module's code ever queries this table;
 * portalUserId links to the genuinely-shared shared.PortalUser, but
 * which clients a person can see is owned here, per module.
 * <p>
 * SCOPE: grants a CLIENT business contact access to their own agency
 * requisitions, placements, and invoices — the direct analog of
 * Payroll Bureau's client portal. Same 7-day invite-token expiry as
 * that confirmed-correct implementation (this draft copies it exactly,
 * not re-guessed from scratch — the earlier module's first attempt at
 * this exact entity shape was missing that expiry and had to be
 * corrected, per Section 53's own note).
 */
@Entity(name = "RecruitmentAgencyPortalAccessGrant")
@Table(name = "reca_portal_access_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecPortalAccessGrant {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
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

    public static RecPortalAccessGrant createInvite(UUID tenantId, UUID clientId,
                                                    String inviteEmail, UUID invitedBy) {
        RecPortalAccessGrant g = new RecPortalAccessGrant();
        g.tenantId = tenantId;
        g.clientId = clientId;
        g.inviteEmail = inviteEmail.toLowerCase().trim();
        g.invitedBy = invitedBy;
        g.invitedAt = Instant.now();
        g.status = "PENDING";
        g.inviteToken = generateToken();
        g.inviteTokenExpiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60);
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
        this.inviteToken = null;
    }

    public void revoke(UUID revokedBy) {
        this.status = "REVOKED";
        this.revokedBy = revokedBy;
        this.revokedAt = Instant.now();
    }
}