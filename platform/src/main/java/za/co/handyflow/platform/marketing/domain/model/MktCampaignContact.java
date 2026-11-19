package za.co.handyflow.platform.marketing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

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

    // First-time-only engagement markers. Writes now go through atomic SQL in
    // MarketingService (UPDATE ... WHERE opened_at IS NULL) so concurrent pixel
    // loads can't double-count; these fields remain mapped for reads.
    @Column(name = "opened_at")  private Instant openedAt;
    @Column(name = "clicked_at") private Instant clickedAt;

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

    public boolean isPending() { return "PENDING".equals(status); }

    public void markSent()                    { this.status = "SENT";  this.sentAt = Instant.now(); }
    public void markBounced(String error)     { this.status = "BOUNCED"; this.errorMessage = error; }
    public void markFailed(String error)      { this.status = "FAILED";  this.errorMessage = error; }
    public void markUnsubscribed()            { this.status = "UNSUBSCRIBED"; }

    /** Recorded eligibility decision: this recipient was excluded and why. */
    public void markSuppressed(String reason) {
        this.status       = "SUPPRESSED";
        this.errorMessage = reason;
    }

    public boolean markOpenedIfFirstTime() {
        if (this.openedAt != null) return false;
        this.openedAt = Instant.now();
        return true;
    }

    public boolean markClickedIfFirstTime() {
        if (this.clickedAt != null) return false;
        this.clickedAt = Instant.now();
        return true;
    }
}