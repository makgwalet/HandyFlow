package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Phase 8d: forecourt sales grouped by the depot that supplies each forecourt. Money fields are null unless the reader may see margin. */
public final class ForecourtSupplyDtos {
    private ForecourtSupplyDtos() {}

    /** One forecourt (the depot holding the pump tanks): litres sold on closed, not-reversed shifts in the period, and the sales value at the price fixed when each shift opened. */
    public record Forecourt(UUID depotId, String name, long shifts, BigDecimal litresSold, BigDecimal salesValue) {}

    /** One supplying depot with the forecourts it supplies. supplierId is null for forecourts with no supplier set (and for tanks in no depot). */
    public record Supplier(UUID supplierId, String name, boolean unassigned, List<Forecourt> forecourts, BigDecimal litresSold, BigDecimal salesValue) {}

    public record Report(LocalDate from, LocalDate to, boolean moneyShown, List<Supplier> suppliers, BigDecimal totalLitres, BigDecimal totalSales, long readingsLeftOut) {}
}
