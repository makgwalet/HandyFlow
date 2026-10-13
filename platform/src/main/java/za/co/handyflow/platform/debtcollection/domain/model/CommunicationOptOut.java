package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A do-not-contact entry for one address/number, optionally for one channel only (channel == null means every
 * channel). contactValue is stored normalised (ContactNormalizer) so "082 123 4567", "+27821234567" and
 * "0821234567" all match. Revoking keeps the row (revokedAt/by/reason) — the history of who opted out and who
 * lifted it must survive. The policy honours every active opt-out for collection messages; whether some
 * account-servicing messages may lawfully still be sent is a decision for counsel, so this is deliberately strict.
 */
@Entity
@Table(name = "debtcollection_communication_opt_outs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunicationOptOut extends AggregateRoot<CommunicationOptOut> {

    @Column(name = "contact_value", nullable = false, length = 255)
    private String contactValue;

    /** null = all channels. */
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private CommunicationChannel channel;

    @Column(nullable = false, length = 500)
    private String reason;

    /** INBOUND_KEYWORD, STAFF or DEBTOR_REQUEST. */
    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "recorded_by_user_name", length = 255)
    private String recordedByUserName;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoke_reason", length = 500)
    private String revokeReason;

    public static CommunicationOptOut of(TenantId tenantId, String normalizedContact, CommunicationChannel channel,
                                         String reason, String source, UUID actorId, String actorName) {
        if (normalizedContact == null || normalizedContact.isBlank()) throw new IllegalArgumentException("contact is required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
        CommunicationOptOut o = new CommunicationOptOut();
        o.initTenantId(tenantId);
        o.contactValue = normalizedContact;
        o.channel = channel;
        o.reason = reason.length() > 500 ? reason.substring(0, 500) : reason;
        o.source = source;
        o.recordedByUserId = actorId;
        o.recordedByUserName = actorName;
        return o;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void revoke(String reason, UUID by) {
        if (!isActive()) throw new IllegalStateException("Opt-out is already revoked");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to revoke an opt-out");
        this.revokedAt = Instant.now();
        this.revokedBy = by;
        this.revokeReason = reason.length() > 500 ? reason.substring(0, 500) : reason;
    }
}
