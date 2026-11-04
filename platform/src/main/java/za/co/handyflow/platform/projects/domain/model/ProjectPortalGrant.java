package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A client contact's access to the Projects client portal: every project of one client, or only chosen projects of it.
 * "Every project" means projects carrying this client id, plus projects whose client name matches (trimmed, any case),
 * because most projects only carry a typed client name. New projects for the client become visible automatically.
 * PENDING (invite sent) → ACTIVE (accepted with the shared portal login) → REVOKED. The invite token is only ever stored hashed.
 */
@Entity
@Table(name = "project_portal_grants")
@Getter
@NoArgsConstructor
public class ProjectPortalGrant {

    public static final long INVITE_DAYS = 14;

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "client_id") UUID clientId;
    @Column(name = "client_name", nullable = false) String clientName;
    @Column(name = "all_projects", nullable = false) boolean allProjects;
    @Column(name = "portal_user_id") UUID portalUserId;
    @Column(name = "invite_email", nullable = false) String inviteEmail;
    @Column(name = "contact_name") String contactName;
    @Column(nullable = false) String status = "PENDING";
    @Column(name = "invite_token_hash") String inviteTokenHash;
    @Column(name = "invite_expires_at") Instant inviteExpiresAt;
    @Column(name = "invited_by") UUID invitedBy;
    @Column(name = "invited_by_name") String invitedByName;
    @Column(name = "invited_at", nullable = false) Instant invitedAt;
    @Column(name = "accepted_at") Instant acceptedAt;
    @Column(name = "last_seen_at") Instant lastSeenAt;
    @Column(name = "revoked_by") UUID revokedBy;
    @Column(name = "revoked_at") Instant revokedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_portal_grant_projects", joinColumns = @JoinColumn(name = "grant_id"))
    @Column(name = "project_id")
    Set<UUID> projectIds = new HashSet<>();

    public static ProjectPortalGrant invite(UUID tenantId, UUID clientId, String clientName, boolean allProjects, Set<UUID> projectIds,
                                            String email, String contactName, String tokenHash, UUID by, String byName) {
        if (clientName == null || clientName.isBlank()) throw new IllegalArgumentException("Choose the client");
        if (!allProjects && (projectIds == null || projectIds.isEmpty())) throw new IllegalArgumentException("Choose at least one project");
        ProjectPortalGrant g = new ProjectPortalGrant();
        g.id = UUID.randomUUID();
        g.tenantId = tenantId; g.clientId = clientId; g.clientName = clientName.trim(); g.allProjects = allProjects;
        if (projectIds != null) g.projectIds.addAll(projectIds);
        g.inviteEmail = email.trim().toLowerCase(); g.contactName = contactName;
        g.invitedBy = by; g.invitedByName = byName; g.invitedAt = Instant.now();
        g.reissue(tokenHash);
        return g;
    }

    /** A fresh invite link (the old one stops working). Only while still pending. */
    public void reissue(String tokenHash) {
        if (!"PENDING".equals(status)) throw new IllegalStateException("Only a pending invite can be sent again");
        this.inviteTokenHash = tokenHash;
        this.inviteExpiresAt = Instant.now().plusSeconds(INVITE_DAYS * 24 * 3600);
    }

    public boolean inviteUsable(Instant now) {
        return "PENDING".equals(status) && inviteTokenHash != null && inviteExpiresAt != null && inviteExpiresAt.isAfter(now);
    }

    public void accept(UUID portalUserId) {
        if (!inviteUsable(Instant.now())) throw new IllegalStateException("This invite has expired or has already been used");
        this.portalUserId = portalUserId; this.status = "ACTIVE"; this.acceptedAt = Instant.now();
        this.inviteTokenHash = null; this.inviteExpiresAt = null;
    }

    public void revoke(UUID by) {
        if ("REVOKED".equals(status)) return;
        this.status = "REVOKED"; this.revokedBy = by; this.revokedAt = Instant.now();
        this.inviteTokenHash = null; this.inviteExpiresAt = null;
    }

    public void changeScope(boolean allProjects, Set<UUID> projectIds) {
        if (!allProjects && (projectIds == null || projectIds.isEmpty())) throw new IllegalArgumentException("Choose at least one project");
        this.allProjects = allProjects;
        this.projectIds.clear();
        if (projectIds != null) this.projectIds.addAll(projectIds);
    }

    public void seen() { this.lastSeenAt = Instant.now(); }
    public boolean isActive() { return "ACTIVE".equals(status); }
}
