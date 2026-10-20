package za.co.handyflow.platform.fleet.dto;

import java.util.UUID;

/** One row of the fleet-wide driver leaderboard. */
public record DriverScoreSummaryResponse(
        UUID driverId,
        String driverName,
        Double overall,
        String band,
        double coverage,
        boolean reliable,
        int tripCount
) {}