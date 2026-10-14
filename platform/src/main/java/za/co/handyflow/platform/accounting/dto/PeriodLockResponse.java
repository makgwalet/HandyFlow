package za.co.handyflow.platform.accounting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The books' lock date and its history (finance patch F5). {@code lockedThrough} null means nothing is closed. */
public record PeriodLockResponse(
        LocalDate lockedThrough,
        UUID updatedBy,
        Instant updatedAt,
        List<Event> history
) {
    public record Event(String action, LocalDate fromDate, LocalDate toDate, String note, UUID byUser, Instant at) {}
}
