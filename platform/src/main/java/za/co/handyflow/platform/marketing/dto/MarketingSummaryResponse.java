package za.co.handyflow.platform.marketing.dto;

public record MarketingSummaryResponse(
        long totalContacts,
        long optedInCount,
        long optedOutCount,
        long draftCampaigns,
        long sentCampaigns,
        long scheduledCampaigns,
        long queuePending
) {}
