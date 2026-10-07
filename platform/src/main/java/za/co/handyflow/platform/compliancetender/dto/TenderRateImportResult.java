package za.co.handyflow.platform.compliancetender.dto;

import java.math.BigDecimal;
import java.util.List;

/** What an import did (or, for a dry run, would do). {@code priceChanges} lists the rates whose cost moved, capped at 100 for display. */
public record TenderRateImportResult(
        boolean dryRun, int created, int updated, int unchanged, int skipped,
        List<PriceChange> priceChanges, List<RowProblem> problems
) {
    public record PriceChange(String description, String unit, String supplier, BigDecimal from, BigDecimal to) {}

    /** {@code line} is the line in the file (the header is line 1); 0 for a problem with the file as a whole. */
    public record RowProblem(int line, String message) {}
}
