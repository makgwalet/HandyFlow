package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * One member (the grantor) lets another member (the holder) vote for them at one meeting. Who may be a grantor or holder, and how many each may have, is decided by
 * {@code ProxyRules}; this class only protects its own state. ACTIVE → REVOKED (final). It is never deleted.
 */
@Entity
@Table(name = "legalcompliance_proxy_appointments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProxyAppointment extends AggregateRoot<ProxyAppointment> {

    public static final int MIN_CLAUSE = 3, MAX_CLAUSE = 200, MAX_INSTRUCTIONS = 500, MIN_REASON = 10;

    @Column(name = "committee_id", nullable = false, updatable = false) private UUID committeeId;
    @Column(name = "meeting_id", nullable = false, updatable = false) private UUID meetingId;
    @Column(name = "grantor_member_id", nullable = false, updatable = false) private UUID grantorMemberId;
    @Column(name = "grantor_name", nullable = false, length = 255, updatable = false) private String grantorName;
    @Column(name = "holder_member_id", nullable = false, updatable = false) private UUID holderMemberId;
    @Column(name = "holder_name", nullable = false, length = 255, updatable = false) private String holderName;
    @Column(name = "charter_clause", nullable = false, length = 200, updatable = false) private String charterClause;
    @Column(length = 500, updatable = false) private String instructions;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 7) private ProxyStatus status = ProxyStatus.ACTIVE;
    @Column(name = "appointed_by_name", length = 255, updatable = false) private String appointedByName;
    @Column(name = "revoked_reason", length = 500) private String revokedReason;
    @Column(name = "revoked_by_name", length = 255) private String revokedByName;
    @Column(name = "revoked_at") private Instant revokedAt;

    public static ProxyAppointment appoint(TenantId t, UUID committeeId, UUID meetingId, UUID grantorId, String grantorName, UUID holderId, String holderName, String clause, boolean charterPermits,
                                           String instructions, String byName) {
        if (!charterPermits) throw new IllegalArgumentException("Confirm that the committee's charter or standing orders allow proxies");
        if (clause == null || clause.trim().length() < MIN_CLAUSE) throw new IllegalArgumentException("Say which clause allows proxies (at least " + MIN_CLAUSE + " characters)");
        if (clause.trim().length() > MAX_CLAUSE) throw new IllegalArgumentException("The clause reference is too long (" + MAX_CLAUSE + " characters at most)");
        if (grantorId == null || holderId == null) throw new IllegalArgumentException("Choose who is giving the proxy and who will hold it");
        if (grantorId.equals(holderId)) throw new IllegalArgumentException("A member cannot be their own proxy");
        if (instructions != null && instructions.trim().length() > MAX_INSTRUCTIONS) throw new IllegalArgumentException("The instructions are too long (" + MAX_INSTRUCTIONS + " characters at most)");
        ProxyAppointment p = new ProxyAppointment();
        p.initTenantId(t);
        p.committeeId = committeeId;
        p.meetingId = meetingId;
        p.grantorMemberId = grantorId;
        p.grantorName = grantorName;
        p.holderMemberId = holderId;
        p.holderName = holderName;
        p.charterClause = clause.trim();
        p.instructions = instructions == null || instructions.isBlank() ? null : instructions.trim();
        p.appointedByName = byName;
        return p;
    }

    public boolean isActive() { return status == ProxyStatus.ACTIVE; }

    public void revoke(String reason, String byName, Instant now) {
        if (!isActive()) throw new IllegalStateException("This proxy has already been revoked");
        if (reason == null || reason.trim().length() < MIN_REASON) throw new IllegalArgumentException("Say why the proxy is being revoked (at least " + MIN_REASON + " characters)");
        this.status = ProxyStatus.REVOKED;
        this.revokedReason = reason.trim().length() > 500 ? reason.trim().substring(0, 500) : reason.trim();
        this.revokedByName = byName;
        this.revokedAt = now;
    }
}
