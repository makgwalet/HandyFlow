package za.co.handyflow.platform.security.dto;

import java.math.BigDecimal;
import java.util.*;

// ── JSON export ────────────────────────────────────────────────────────────────

public record PayrollExportJsonResponse(
        UUID   periodId,
        String periodName,
        String periodStart,
        String periodEnd,
        int    totalLineItems,
        BigDecimal totalHours,
        Long   totalAmountCents,
        Double totalAmountZar,
        List<GuardPaySummary> guardSummaries
) {
    public record GuardPaySummary(
            UUID   guardId,
            String guardName,
            String grade,
            String psiraNumber,
            int    shiftCount,
            BigDecimal totalHours,
            Long   grossAmountCents,
            Double grossAmountZar
    ) {}
}
