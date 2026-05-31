package za.co.handyflow.platform.marketing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// ── MktCampaignContact ────────────────────────────────────────────────────────
@Entity
@Table(name = "mkt_campaign_contacts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MktCampaignContact {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "campaign_id", nullable = false) private UUID    campaignId;
    @Column(name = "tenant_id",   nullable = false) private UUID    tenantId;
    @Column(nullable = false)                         private String  email;
    private String name;
    @Column(name = "preference_id") private UUID preferenceId;
    @Column(nullable = false)       private String status = "PENDING";
    @Column(name = "sent_at")       private Instant sentAt;
    @Column(name = "error_message") private String  errorMessage;

    public static MktCampaignContact create(UUID campaignId, UUID tenantId,
                                             String email, String name, UUID preferenceId) {
        MktCampaignContact c = new MktCampaignContact();
        c.campaignId   = campaignId;
        c.tenantId     = tenantId;
        c.email        = email.toLowerCase().trim();
        c.name         = name;
        c.preferenceId = preferenceId;
        c.status       = "PENDING";
        return c;
    }

    public void markSent()                    { this.status = "SENT";  this.sentAt = Instant.now(); }
    public void markBounced(String error)     { this.status = "BOUNCED"; this.errorMessage = error; }
    public void markFailed(String error)      { this.status = "FAILED";  this.errorMessage = error; }
    public void markUnsubscribed()            { this.status = "UNSUBSCRIBED"; }
}
