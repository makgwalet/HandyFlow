package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Suppliers and what stock receipts cost by supplier (ADR-001, W7). Supply Chain owns suppliers; Agriculture only refers to them. */
public final class PurchasesDtos {

    private PurchasesDtos() {}

    /** A supplier that can be chosen for a purchase: an ACTIVE supplier in Supply Chain. Identity only; never banking or contact details. */
    public record SupplierOption(UUID id, String name) {}

    /** Sets an item's usual supplier; null clears it. */
    public record SetSupplierRequest(UUID supplierId) {}

    /**
     * @param supplierId null for the "no supplier recorded" row
     * @param sharePercent this supplier's share of the total spend to one decimal; null when the total is zero
     */
    public record SupplierSpend(UUID supplierId, String supplierName, int receipts, BigDecimal spend, BigDecimal sharePercent) {}

    /**
     * @param receiptsWithoutCost receipts with no unit cost recorded: they are in the receipt count but count as no spend
     * @param notes               what this figure is and is not (it is stock received into Agriculture, not Supply Chain purchase orders or invoices)
     */
    public record SupplierSpendResponse(LocalDate from, LocalDate to, List<SupplierSpend> suppliers, BigDecimal totalSpend, int receipts, int receiptsWithoutCost, List<String> notes) {}
}
