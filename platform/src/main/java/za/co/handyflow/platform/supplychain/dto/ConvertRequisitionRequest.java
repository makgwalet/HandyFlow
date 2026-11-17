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
    public record Line(UUID requisitionLineId, BigDecimal quantity, BigDecimal unitCost, BigDecimal vatRate) {}
}
