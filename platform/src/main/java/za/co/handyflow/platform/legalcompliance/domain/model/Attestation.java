package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One person's response to one campaign. A person can change their answer while the campaign is open; the latest answer counts and every change is in the event log. */
@Entity
@Table(name = "legalcompliance_attestations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attestation extends AggregateRoot<Attestation> {

    public static final int MIN_COMMENT = 15;

    @Column(name = "campaign_id", nullable = false) private UUID campaignId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "user_name", nullable = false, length = 255) private String userName;
    @Column(name = "user_email", length = 255) private String userEmail;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private AttestationStatus status = AttestationStatus.PENDING;
    @Column(name = "responded_at") private Instant respondedAt;
    @Column(columnDefinition = "TEXT") private String comment;
    @Column(name = "content_hash", length = 64) private String contentHash;

    public static Attestation assign(TenantId tenantId, UUID campaignId, UUID userId, String userName, String userEmail) {
        if (userId == null) throw new IllegalArgumentException("A user is required");
        Attestation a = new Attestation();
        a.initTenantId(tenantId);
        a.campaignId = campaignId;
        a.userId = userId;
        a.userName = userName == null || userName.isBlank() ? "Unknown" : userName.trim();
        a.userEmail = userEmail;
        return a;
    }

    public void respond(boolean accept, String comment, String campaignHash) {
        if (!accept && (comment == null || comment.trim().length() < MIN_COMMENT)) throw new IllegalArgumentException("Say why you cannot accept (at least " + MIN_COMMENT + " characters)");
        this.status = accept ? AttestationStatus.ACCEPTED : AttestationStatus.DECLINED;
        this.comment = comment == null || comment.isBlank() ? null : comment.trim();
        this.contentHash = campaignHash;
        this.respondedAt = Instant.now();
    }

    public boolean isPending() { return status == AttestationStatus.PENDING; }
}
