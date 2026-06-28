package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ImportJobResult — returned by POST /import (202) and GET /import/{id}.
 *
 * WHY include rowErrors in the poll response and not just counts?
 * The user needs to know WHICH rows failed and WHY so they can fix
 * their CSV and re-import.  Returning "3 rows skipped" with no detail
 * forces them to guess — bad UX.
 *
 * WHY List<RowError> and not a separate errors endpoint?
 * For import jobs up to 2,000 rows, the error list is small enough to
 * embed in the job result.  If you later support 50k-row imports, split
 * errors into a paginated sub-resource.
 */
public record ImportJobResult(
        UUID jobId,
        String      status,        // PENDING | PROCESSING | DONE | FAILED
        String      filename,
        int         totalRows,
        int         createdCount,
        int         skippedCount,
        int         errorCount,
        List<RowError> rowErrors,
        Instant startedAt,
        Instant     completedAt
) {
    public record RowError(int row, String name, String reason) {}
}