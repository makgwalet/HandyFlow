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

    // NEW: per-recipient open/click tracking. Tracks UNIQUE opens/clicks
    // (first time only) rather than every image load or link click — that's
    // what "open rate" and "click rate" mean industry-wide (% of recipients
    // who engaged at least once), not a raw event count that double-counts
    // someone who opened the same email three times.
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

    public void markSent()                    { this.status = "SENT";  this.sentAt = Instant.now(); }
    public void markBounced(String error)     { this.status = "BOUNCED"; this.errorMessage = error; }
    public void markFailed(String error)      { this.status = "FAILED";  this.errorMessage = error; }
    public void markUnsubscribed()            { this.status = "UNSUBSCRIBED"; }

    /** No-op after the first call — returns false so callers know not to double-count. */
    public boolean markOpenedIfFirstTime() {
        if (this.openedAt != null) return false;
        this.openedAt = Instant.now();
        return true;
    }

    /** Same first-time-only semantics as markOpenedIfFirstTime(). */
    public boolean markClickedIfFirstTime() {
        if (this.clickedAt != null) return false;
        this.clickedAt = Instant.now();
        return true;
    }
}
