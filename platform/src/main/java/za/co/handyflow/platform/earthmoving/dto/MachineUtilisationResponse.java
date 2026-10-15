package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Hours worked per hour-metered machine in a period. {@code daysWorkedPercent} is the share of the period's days with at least one shift. */
public record MachineUtilisationResponse(LocalDate from, LocalDate to, int days, BigDecimal totalHours, List<Row> machines) {
    public record Row(UUID assetId, String machine, int shifts, BigDecimal hours, int daysWorked, BigDecimal daysWorkedPercent) {}
}
