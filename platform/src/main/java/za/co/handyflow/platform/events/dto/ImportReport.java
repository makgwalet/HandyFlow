package za.co.handyflow.platform.events.dto;

import java.util.List;

public record ImportReport(
        boolean dryRun,        // true = nothing was saved; this is exactly what WOULD happen
        int totalRows,
        int imported,
        int duplicates,
        int invalid,
        int rejected,
        List<ImportRowResult> rows
) {
    /**
     * @param row          line position in the file (row 2 = first data row, row 1 = header)
     * @param outcome      IMPORTED | DUPLICATE | INVALID | REJECTED
     * @param ticketNumber only set for a real (non-dry-run) import
     */
    public record ImportRowResult(
            int row,
            String fullName,
            String email,
            String outcome,
            String message,
            String ticketNumber
    ) {}
}