package za.co.handyflow.platform.agriculture.dto;

import java.util.List;

/** What a stock import did (or, for a dry run, would do). {@code ready} counts rows that were created, or would be. */
public record StockImportResponse(
        boolean dryRun,
        int total,
        int ready,
        int duplicates,
        int errors,
        List<RowResult> rows
) {
    /** {@code line} is 1 for the first item in the file. {@code status} is OK, DUPLICATE or ERROR. */
    public record RowResult(int line, String itemName, String status, String message) {}
}
