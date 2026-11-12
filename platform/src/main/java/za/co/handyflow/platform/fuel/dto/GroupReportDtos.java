package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Phase 6a: a read-only report across the group's legal entities. Money fields are null unless the reader may see margin. */
public final class GroupReportDtos {
    private GroupReportDtos() {}

    /**
     * One legal entity (or the "not assigned" bucket, or the group total when legalEntityId is null and unassigned is false).
     * Litres and counts are for the period; the previous* fields are the same measure for the period of equal length just before it.
     * Stock and open variances are as at now. revenue counts every delivered, not-reversed delivery; margin counts only those with a cost at sale.
     */
    public record Row(UUID legalEntityId, String name, boolean unassigned,
                      long deliveries, BigDecimal litres, BigDecimal revenue, BigDecimal margin, long deliveriesWithoutCost,
                      long previousDeliveries, BigDecimal previousLitres, BigDecimal previousRevenue, BigDecimal previousMargin,
                      BigDecimal stockLitres, BigDecimal stockValue, long tanksWithoutCost, long openVariances) {}

    public record Report(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, boolean moneyShown,
                         List<Row> entities, Row total, long deliveriesLeftOut) {}
}
