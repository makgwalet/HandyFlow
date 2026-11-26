package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.PortalRules;

import java.time.Instant;
import java.util.UUID;

/** Says which portal login may look at which one learner's own records. Ended with a reason, never deleted. */
@Entity(name = "TrainProvLearnerPortalGrant")
@Table(name = "trainprov_learner_portal_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLearnerPortalGrant {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;
    @Column(name = "invite_email", nullable = false, updatable = false)
    private String inviteEmail;
    @Column(name = "invite_token", nullable = false, updatable = false)
    private String inviteToken;
    @Column(name = "invite_expires_at", nullable = false, updatable = false)
    private Instant inviteExpiresAt;
    @Column(name = "invite_reason", nullable = false, updatable = false)
    private String inviteReason;
    @Column(name = "invited_by", nullable = false, updatable = false)
    private UUID invitedBy;
    @Column(name = "portal_user_id")
    private UUID portalUserId;
    @Column(name = "accepted_at")
    private Instant acceptedAt;
    @Column(nullable = false)
    private String status = "PENDING";
    @Column(name = "ended_reason")
    private String endedReason;
    @Column(name = "ended_by")
    private UUID endedBy;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvLearnerPortalGrant invite(TenantId t, UUID learnerId, String email, String token, Instant expires, String reason, UUID by, String learnerStatus) {
        String problem = PortalRules.inviteProblem(email, reason, learnerStatus);
        if (problem != null) throw new IllegalArgumentException(problem);
        TrainProvLearnerPortalGrant g = new TrainProvLearnerPortalGrant();
        g.tenantId = t.getValue();
        g.learnerId = learnerId;
        g.inviteEmail = email.trim().toLowerCase();
        g.inviteToken = token;
        g.inviteExpiresAt = expires;
        g.inviteReason = reason.trim();
        g.invitedBy = by;
        g.createdAt = Instant.now();
        g.updatedAt = g.createdAt;
        return g;
    }

    public boolean isInviteValid() {
        return "PENDING".equals(status) && Instant.now().isBefore(inviteExpiresAt);
    }

    public void accept(UUID userId) {
        if (!isInviteValid()) throw new IllegalStateException("This invite is no longer valid");
        this.portalUserId = userId;
        this.acceptedAt = Instant.now();
        this.status = "ACCEPTED";
        this.updatedAt = this.acceptedAt;
    }

    public void end(String reason, UUID by) {
        String problem = PortalRules.endProblem(reason, status);
        if (problem != null) throw new IllegalStateException(problem);
        this.status = "ENDED";
        this.endedReason = reason.trim();
        this.endedBy = by;
        this.endedAt = Instant.now();
        this.updatedAt = this.endedAt;
    }
}
