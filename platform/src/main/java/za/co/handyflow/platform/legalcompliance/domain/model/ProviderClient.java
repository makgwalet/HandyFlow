package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalcompliance.application.internal.ProviderRules;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * A client organisation of a compliance provider. The row belongs to the PROVIDER tenant; the client's own tenant is only recorded when the client accepts the invitation.
 * INVITED → ACTIVE (client accepts, choosing scopes) ⇄ SUSPENDED (either side; only that side may lift it) → ENDED (either side, final). Never deleted.
 */
@Entity
@Table(name = "legalcompliance_provider_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProviderClient extends AggregateRoot<ProviderClient> {

    public static final int MIN_REASON = 10;

    @Column(nullable = false, length = 20) private String reference;
    @Column(name = "client_name", nullable = false, length = 255) private String clientName;
    /** How the provider presents itself to the client; shown to the client when it is asked to accept. The invite code, passed out of band, is what proves it is genuine. */
    @Column(name = "provider_name", nullable = false, length = 255) private String providerName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private ProviderClientStatus status = ProviderClientStatus.INVITED;
    @Column(name = "invite_hash", length = 64) private String inviteHash;
    @Column(name = "invite_expires_at") private Instant inviteExpiresAt;
    @Column(name = "requested_scopes", nullable = false, length = 30) private String requestedScopes;
    @Column(name = "granted_scopes", nullable = false, length = 30) private String grantedScopes = "";
    @Column(name = "client_tenant_id") private UUID clientTenantId;
    @Column(name = "accepted_by_user_id") private UUID acceptedByUserId;
    @Column(name = "accepted_by_name", length = 255) private String acceptedByName;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Enumerated(EnumType.STRING) @Column(name = "suspended_by", length = 10) private AccessSide suspendedBy;
    @Column(name = "status_reason", columnDefinition = "TEXT") private String statusReason;
    @Column(name = "contract_start") private LocalDate contractStart;
    @Column(name = "contract_end") private LocalDate contractEnd;
    @Enumerated(EnumType.STRING) @Column(name = "review_cycle", nullable = false, length = 10) private ReviewCycle reviewCycle = ReviewCycle.QUARTERLY;
    @Column(name = "next_review_due") private LocalDate nextReviewDue;
    @Column(name = "ended_at") private Instant endedAt;
    @Enumerated(EnumType.STRING) @Column(name = "ended_by", length = 10) private AccessSide endedBy;
    @Column(columnDefinition = "TEXT") private String notes;

    public static ProviderClient invite(TenantId provider, String reference, String providerName, String clientName, Set<AccessScope> requested, LocalDate contractStart, LocalDate contractEnd,
                                        ReviewCycle cycle, String notes, String codeHash, Instant now) {
        ProviderClient c = new ProviderClient();
        c.initTenantId(provider);
        c.reference = reference;
        if (providerName == null || providerName.isBlank()) throw new IllegalArgumentException("Your organisation's name, as the client should see it, is required");
        c.providerName = providerName.trim();
        c.requestedScopes = scopes(requested);
        c.applyDetails(clientName, contractStart, contractEnd, cycle, notes);
        c.inviteHash = codeHash;
        c.inviteExpiresAt = now.plus(java.time.Duration.ofDays(ProviderRules.INVITE_DAYS));
        return c;
    }

    private static String scopes(Set<AccessScope> s) {
        if (s == null || s.isEmpty()) throw new IllegalArgumentException("Choose at least one thing you are asking to see");
        return ProviderRules.scopesToCsv(s);
    }

    private void applyDetails(String name, LocalDate start, LocalDate end, ReviewCycle cycle, String notes) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("The client's name is required");
        if (cycle == null) throw new IllegalArgumentException("A review cycle is required");
        if (start != null && end != null && end.isBefore(start)) throw new IllegalArgumentException("The contract cannot end before it starts");
        this.clientName = name.trim();
        this.contractStart = start;
        this.contractEnd = end;
        this.reviewCycle = cycle;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void update(String name, LocalDate start, LocalDate end, ReviewCycle cycle, String notes) {
        if (status == ProviderClientStatus.ENDED) throw new IllegalStateException("An ended relationship cannot be edited");
        applyDetails(name, start, end, cycle, notes);
    }

    /** New code for an invitation that has not been accepted (lost or expired). Re-asking for different scopes is allowed here only. */
    public void reissueInvite(String newHash, Set<AccessScope> requested, Instant now) {
        if (status != ProviderClientStatus.INVITED) throw new IllegalStateException("Only an invitation that has not been accepted can be reissued");
        this.requestedScopes = scopes(requested);
        this.inviteHash = newHash;
        this.inviteExpiresAt = now.plus(java.time.Duration.ofDays(ProviderRules.INVITE_DAYS));
    }

    /** Client side. Granted scopes must be within what was requested and cannot be empty. The code is cleared so it cannot be used again. */
    public void accept(UUID clientTenant, Set<AccessScope> granted, UUID userId, String userName, Instant now, LocalDate today) {
        if (status != ProviderClientStatus.INVITED) throw new IllegalStateException("This invitation has already been used or withdrawn");
        if (ProviderRules.inviteExpired(inviteExpiresAt, now)) throw new IllegalStateException("This invitation has expired. Ask the provider to send a new one");
        if (clientTenant == null) throw new IllegalArgumentException("The client organisation is required");
        if (clientTenant.equals(getTenantId().getValue())) throw new IllegalArgumentException("An organisation cannot be its own provider");
        if (granted == null || granted.isEmpty()) throw new IllegalArgumentException("Grant at least one thing, or decline the invitation");
        if (!ProviderRules.parseScopes(requestedScopes).containsAll(granted)) throw new IllegalArgumentException("You can only grant what the provider asked for");
        this.clientTenantId = clientTenant;
        this.grantedScopes = ProviderRules.scopesToCsv(granted);
        this.acceptedByUserId = userId;
        this.acceptedByName = userName;
        this.acceptedAt = now;
        this.status = ProviderClientStatus.ACTIVE;
        this.inviteHash = null;
        this.inviteExpiresAt = null;
        if (nextReviewDue == null) this.nextReviewDue = ProviderRules.nextReview(today, reviewCycle);
    }

    public void suspend(AccessSide side, String reason) {
        if (status != ProviderClientStatus.ACTIVE) throw new IllegalStateException("Only active access can be suspended");
        this.suspendedBy = requireSide(side);
        this.statusReason = requireReason(reason);
        this.status = ProviderClientStatus.SUSPENDED;
    }

    public void resume(AccessSide side) {
        if (status != ProviderClientStatus.SUSPENDED) throw new IllegalStateException("Only suspended access can be resumed");
        if (!ProviderRules.canResume(suspendedBy == null ? null : suspendedBy.name(), requireSide(side).name()))
            throw new IllegalStateException("Access suspended by the " + suspendedBy.name().toLowerCase() + " can only be resumed by the " + suspendedBy.name().toLowerCase());
        this.suspendedBy = null;
        this.statusReason = null;
        this.status = ProviderClientStatus.ACTIVE;
    }

    /** An invitation that was never accepted can be withdrawn by the provider; accepted access can be ended by either side. */
    public void end(AccessSide side, String reason, Instant now) {
        if (status == ProviderClientStatus.ENDED) throw new IllegalStateException("This relationship has already ended");
        if (status == ProviderClientStatus.INVITED && side != AccessSide.PROVIDER) throw new IllegalStateException("Only the provider can withdraw an invitation");
        this.endedBy = requireSide(side);
        this.statusReason = requireReason(reason);
        this.status = ProviderClientStatus.ENDED;
        this.suspendedBy = null;
        this.inviteHash = null;
        this.inviteExpiresAt = null;
        this.endedAt = now;
    }

    public void scheduleReview(LocalDate reviewedOn) {
        if (status != ProviderClientStatus.ACTIVE && status != ProviderClientStatus.SUSPENDED) throw new IllegalStateException("Reviews are recorded for clients with accepted access");
        this.nextReviewDue = ProviderRules.nextReview(reviewedOn, reviewCycle);
    }

    public Set<AccessScope> scopesGranted() { return ProviderRules.parseScopes(grantedScopes); }
    public Set<AccessScope> scopesRequested() { return ProviderRules.parseScopes(requestedScopes); }

    private static AccessSide requireSide(AccessSide s) { if (s == null) throw new IllegalArgumentException("The acting side is required"); return s; }

    private static String requireReason(String r) {
        if (r == null || r.trim().length() < MIN_REASON) throw new IllegalArgumentException("A reason of at least " + MIN_REASON + " characters is required");
        return r.trim();
    }
}
