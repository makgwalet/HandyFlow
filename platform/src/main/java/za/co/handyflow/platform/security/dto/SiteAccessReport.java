package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SiteAccessReport — the fourth security report, alongside
 * SiteCoverageReport/GuardAttendanceReport/MonthlySummaryReport. Same
 * record shape convention as those three.
 * <p>
 * idNumber is deliberately absent from the line-item record —
 * consistent with masking on every other read path for this data (the
 * response DTO, the client portal), a PDF that leaves this device is
 * exactly the kind of artifact POPIA's own data-minimization principle
 * is concerned with, and a printed/emailed report is harder to control
 * the spread of than an in-app response.
 */
public record SiteAccessReport(
        UUID siteId, String siteName, String month,
        int totalEntries, int currentlyOnSite, int departed, int overstayed,
        Map<String, Long> entriesByType,
        List<EntryLine> entries
) {
    public record EntryLine(
            String entryType, String personName, String company,
            String vehicleRegistration, String accessPointName,
            Instant loggedInAt, Instant loggedOutAt, String status
    ) {}
}