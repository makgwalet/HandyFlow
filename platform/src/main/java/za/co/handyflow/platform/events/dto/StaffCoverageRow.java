package za.co.handyflow.platform.events.dto;

/** Per role: how many are rostered, how many of those have confirmed, how many have turned up, and no-shows. */
public record StaffCoverageRow(
        String role,
        int rostered,       // assigned + confirmed + arrived
        int confirmed,      // confirmed + arrived
        int arrived,
        int noShow
) {}