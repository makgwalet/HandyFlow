package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** One person in a campaign, how far they have got, and how far through the journey. */
@Entity
@Table(name = "crm_campaign_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignMember {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private MemberStatus status = MemberStatus.ADDED;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "status_at", nullable = false)
    private Instant statusAt;

    @Column(name = "steps_done", nullable = false)
    private int stepsDone;

    @Column(name = "exited_at")
    private Instant exitedAt;

    public static CampaignMember of(TenantId tenantId, UUID campaignId, UUID customerId) {
        var m = new CampaignMember();
        m.tenantId = tenantId;
        m.campaignId = campaignId;
        m.customerId = customerId;
        m.addedAt = Instant.now();
        m.statusAt = m.addedAt;
        return m;
    }

    public void setStatus(MemberStatus status) {
        if (this.status == status) return;
        this.status = status;
        this.statusAt = Instant.now();
        if (status == MemberStatus.RESPONDED || status == MemberStatus.CONVERTED) exit();
    }

    public void stepsDone(int n) { this.stepsDone = n; }

    public void exit() { if (exitedAt == null) this.exitedAt = Instant.now(); }

    public boolean isExited() { return exitedAt != null; }
}
