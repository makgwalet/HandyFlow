package za.co.handyflow.platform.events.dto;

public record EventStatsResponse(
        long totalRegistered,
        long totalCheckedIn,
        long totalCancelled,
        long totalVendors,
        long confirmedVendors
) {}