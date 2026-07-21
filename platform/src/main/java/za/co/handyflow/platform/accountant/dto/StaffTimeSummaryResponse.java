package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StaffTimeSummaryResponse(
        UUID practitionerId,
        String practitionerName,
        BigDecimal totalHours,
        BigDecimal billableHours,
        BigDecimal totalBilled,
        long entryCount
) {
}