package za.co.handyflow.platform.marketing.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    /** All counts are UNIQUE recipients. Rates are 0..1, or null when the denominator is zero. */
    public record CampaignMetrics(
            UUID id, String name, String status, int contentVersion, Instant createdAt, Instant sentAt,
            long targeted, long sent, long delivered, long bounced, long complained,
            long opened, long clicked, long unsubscribed, long failed, long suppressed, long pending,
            Double openRate, Double clickRate, Double clickToOpenRate, Double bounceRate,
            Double complaintRate, Double unsubscribeRate, Double deliveryRate,
            boolean deliveryConfirmed) {}

    public record Overview(int days, int campaigns, CampaignMetrics totals, List<CampaignMetrics> items) {}

    public record TimelinePoint(String date, long opened, long clicked) {}

    public record LinkStat(String url, long uniqueClickers, long totalClicks, Instant lastClickedAt) {}

    public record RecipientRow(
            UUID id, String email, String name, String status, String deliveryStatus,
            Instant sentAt, Instant deliveredAt, Instant bouncedAt, Instant complainedAt,
            Instant openedAt, Instant clickedAt, String error) {}

    public record ReconDiff(String metric, long stored, long actual) {}

    public record Reconciliation(String campaignStatus, boolean inSync, boolean repairable,
                                 List<ReconDiff> diffs, List<String> notes) {}

    public record MetricDefinition(String key, String label, String definition, String caveat) {}
}