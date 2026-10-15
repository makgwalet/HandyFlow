package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Time out of service per machine in a period. Hours are rounded to one decimal; availability is null before the period starts. */
public record DowntimeResponse(LocalDate from, LocalDate to, Row totals, List<Row> machines) {
    public record Row(UUID assetId, String machine, int breakdowns, BigDecimal breakdownHours, BigDecimal maintenanceHours,
                      BigDecimal totalHours, BigDecimal availabilityPercent) {}
}
