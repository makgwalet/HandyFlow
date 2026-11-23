package za.co.handyflow.platform.agriculture.dto;

import java.util.List;

/** What an import did (or, for a dry run, would do). {@code ready} counts rows that were created, or would be. */
public record AnimalImportResponse(
        boolean dryRun,
        int total,
        int ready,
        int duplicates,
        int errors,
        List<RowResult> rows
) {
    /** {@code line} is 1 for the first animal in the file. {@code status} is OK, DUPLICATE or ERROR. */
    public record RowResult(int line, String tagNumber, String status, String message) {}
}
