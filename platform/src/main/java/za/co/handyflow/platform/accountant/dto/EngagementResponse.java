package za.co.handyflow.platform.accountant.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A client's financial year and where it stands (finance patch F15). */
public record EngagementResponse(
        UUID id, UUID clientId, int financialYear, LocalDate yearStart, LocalDate yearEnd, String status,
        UUID preparedBy, String preparedByName, Instant preparedAt,
        UUID reviewedBy, String reviewedByName, Instant reviewedAt,
        UUID signedBy, String signedByName, Instant signedAt,
        long unpostedJournals, List<String> warnings, List<Event> events
) {
    public record Event(String action, UUID byUser, String byName, String note, Instant at) {}
}
