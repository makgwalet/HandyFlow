package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WipSummaryResponse(
        UUID clientId,
        String clientName,
        BigDecimal unbilledHours,
        BigDecimal wipValue,
        LocalDate oldestEntryDate
) {
}
