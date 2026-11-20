package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** The vote a holder casts for a grantor on one decision. For, against or abstain only: a holder cannot recuse on someone else's behalf. Changed only while the ballot is open. */
@Entity
@Table(name = "legalcompliance_proxy_votes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProxyVote extends AggregateRoot<ProxyVote> {

    @Column(name = "proxy_id", nullable = false, updatable = false) private UUID proxyId;
    @Column(name = "resolution_id", nullable = false, updatable = false) private UUID resolutionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 7) private VoteChoice choice;
    @Column(length = 500) private String reason;
    @Column(name = "recorded_by_name", length = 255) private String recordedByName;

    public static ProxyVote cast(TenantId t, UUID proxyId, UUID resolutionId, VoteChoice choice, String reason, String byName) {
        ProxyVote v = new ProxyVote();
        v.initTenantId(t);
        v.proxyId = proxyId;
        v.resolutionId = resolutionId;
        v.change(choice, reason, byName);
        return v;
    }

    public void change(VoteChoice choice, String reason, String byName) {
        if (choice == null) throw new IllegalArgumentException("A vote is required");
        if (choice == VoteChoice.RECUSED) throw new IllegalArgumentException("A proxy holder cannot recuse on someone else's behalf. Vote for, against or abstain");
        this.choice = choice;
        String r = reason == null || reason.isBlank() ? null : reason.trim();
        this.reason = r == null ? null : r.length() > 500 ? r.substring(0, 500) : r;
        this.recordedByName = byName;
    }
}
