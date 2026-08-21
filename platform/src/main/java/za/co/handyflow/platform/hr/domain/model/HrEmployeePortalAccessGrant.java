package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * FIX: backlog 3.4 — "employee self-service reality unconfirmed." Confirmed
 * genuinely absent (see the investigation write-up in chat): HrEmployee is
 * deliberately not a platform User, every HR endpoint requires staff-level
 * authorities, and even payrollbureau.PayPortalAccessGrant's own Javadoc
 * explicitly flagged this exact feature as "deliberately not built here."
 * <p>
 * Direct structural mirror of payrollbureau.PayPortalAccessGrant /
 * accountant.AccPortalAccessGrant / auditor's equivalent — same 7-day
 * invite-token expiry, same PENDING/ACTIVE/REVOKED status shape. Only
 * this module's code ever queries this table. portalUserId links to the
 * genuinely-shared shared.PortalUser (the login identity itself is
 * shared across every portal in this codebase); which employee record a
 * person can see is owned here, per module — same "shared identifier,
 * not shared entity" reasoning as every other portal grant.
 * <p>
 * SCOPE NOTE — the deliberate difference from every other portal grant
 * in this codebase: this is scoped to exactly ONE employee record
 * (employeeId), not a whole client's data (payClientId/clientId
 * elsewhere). The access shape here is "one person, their own record,"
 * not "one business contact, everything belonging to their company."
 */
@Entity(name = "HrEmployeePortalAccessGrant")
@Table(name = "hr_employee_portal_access_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HrEmployeePortalAccessGrant {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "employee_id", nullable = false) private UUID employeeId;
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

    public static HrEmployeePortalAccessGrant createInvite(UUID tenantId, UUID employeeId,
                                                           String inviteEmail, UUID invitedBy) {
        HrEmployeePortalAccessGrant g = new HrEmployeePortalAccessGrant();
        g.tenantId = tenantId;
        g.employeeId = employeeId;
        g.inviteEmail = inviteEmail.toLowerCase().trim();
        g.invitedBy = invitedBy;
        g.invitedAt = Instant.now();
        g.status = "PENDING";
        g.inviteToken = generateToken();
        g.inviteTokenExpiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60); // 7 days, same as every other portal
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