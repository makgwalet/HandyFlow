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

    /**
     * Reconciliation for one forecourt: where its fuel came from in the period. All litres, no money.
     * supplierName is the depot recorded as supplying it (null if none). Shares are percentages of total inbound litres, null when nothing came in.
     */
    public record Inbound(UUID depotId, String name, UUID supplierId, String supplierName,
                          BigDecimal fromSupplierDepot, BigDecimal fromOtherDepots, BigDecimal fromSuppliers, BigDecimal totalIn,
                          BigDecimal supplierDepotSharePct, BigDecimal litresSold) {}

    public record Reconciliation(LocalDate from, LocalDate to, List<Inbound> forecourts, BigDecimal totalIn, BigDecimal totalSold) {}

    /** One line of a forecourt's supply split, as saved. */
    public record ShareLine(UUID supplyingDepotId, BigDecimal percentage) {}

    /** One line of a forecourt's supply split, as read back. */
    public record Share(UUID supplyingDepotId, String name, BigDecimal percentage) {}

    /** A dispatch to a forecourt with the receipts linked to it. difference = litres dispatched minus litres received (positive: less arrived than left). */
    public record Matched(UUID dispatchId, java.time.Instant dispatchedAt, String fromTank, String fuelType, BigDecimal litresDispatched, BigDecimal litresReceived, BigDecimal difference, List<LinkedReceipt> receipts, boolean byTransfer) {
        public Matched(UUID dispatchId, java.time.Instant dispatchedAt, String fromTank, String fuelType, BigDecimal litresDispatched, BigDecimal litresReceived, BigDecimal difference, List<LinkedReceipt> receipts) {
            this(dispatchId, dispatchedAt, fromTank, fuelType, litresDispatched, litresReceived, difference, receipts, false);
        }
    }

    public record LinkedReceipt(UUID matchId, UUID receiptId, java.time.Instant receivedAt, BigDecimal litres, String deliveryNote) {}

    /** A dispatch to the forecourt that no receipt is linked to yet. */
    public record OpenDispatch(UUID dispatchId, java.time.Instant dispatchedAt, String fromTank, String fuelType, BigDecimal litres) {}

    /** A receipt into a forecourt tank that is not linked to a dispatch. */
    public record OpenReceipt(UUID receiptId, java.time.Instant receivedAt, String tank, String fuelType, BigDecimal litres, String deliveryNote) {}

    public record ForecourtMatching(UUID depotId, String name, List<Matched> matched, List<OpenDispatch> openDispatches, List<OpenReceipt> openReceipts,
                                    BigDecimal dispatchedLitres, BigDecimal receivedLitres) {}

    public record MatchingReport(LocalDate from, LocalDate to, List<ForecourtMatching> forecourts) {}

    public record MatchRequest(UUID dispatchId, UUID receiptId) {}
}
