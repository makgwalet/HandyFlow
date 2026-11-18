package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Turns (part of) an APPROVED requisition into a DRAFT purchase order with one supplier.
 * Call again with another supplier for the remaining lines. quantity defaults to the line's
 * remaining quantity; unitCost defaults to the requisition's estimate.
 */
public record ConvertRequisitionRequest(
        UUID supplierId,
        UUID deliverToLocation,
        LocalDate requiredByDate,
        String notes,
        List<Line> lines
) {
    /**
     * unitCost differs from the requisition estimate: priceChangeReason is then required (SUPPLIER_QUOTATION,
     * NEGOTIATED_DISCOUNT, MARKET_PRICE_CHANGE, CONTRACT_PRICE, SPECIFICATION_CHANGE, OTHER; OTHER needs a note).
     * quotationRef is the supplier quotation or document the price came from.
     */
    public record Line(UUID requisitionLineId, BigDecimal quantity, BigDecimal unitCost, BigDecimal vatRate,
                       String priceChangeReason, String priceChangeNote, String quotationRef) {
        public Line(UUID requisitionLineId, BigDecimal quantity, BigDecimal unitCost, BigDecimal vatRate) {
            this(requisitionLineId, quantity, unitCost, vatRate, null, null, null);
        }
    }
}
