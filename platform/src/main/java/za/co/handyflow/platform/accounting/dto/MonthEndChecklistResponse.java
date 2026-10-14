package za.co.handyflow.platform.accounting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The close checklist for one month end (finance patch F13). {@code closeProblem} is null when the month can be closed. */
public record MonthEndChecklistResponse(
        LocalDate periodEnd, LocalDate lockedThrough, boolean closable, String closeProblem, List<Item> items
) {
    public record Item(String key, String title, String kind, String status, String detail, boolean blocking,
                       UUID confirmedBy, Instant confirmedAt) {}
}
