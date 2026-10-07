package za.co.handyflow.platform.clinic.dto;

import java.util.List;

/** One page of recalls plus counts over the whole (unfiltered) worklist, so the tabs can show totals. */
public record RecallPage(
        List<RecallResponse> content,
        int page,
        int size,
        int total,
        Counts counts
) {
    public record Counts(int open, int overdue, int dueToday, int notContacted, int snoozed) {}
}
