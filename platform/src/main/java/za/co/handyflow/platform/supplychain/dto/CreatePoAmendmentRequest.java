package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Raise an amendment against an approved PO. Header fields left null mean "unchanged".
 * A line change's action is ADD, UPDATE or REMOVE; for UPDATE, null qty/cost/vat mean "leave as is".
 */
public record CreatePoAmendmentRequest(
        String reason,
        LocalDate requiredByDate,
        UUID deliverToLocation,
        String notes,
        String terms,
        List<LineChange> lineChanges
) {
    public record LineChange(
            String action,
            UUID poLineId,
            UUID catalogueItemId,
            String itemName,
            String supplierSku,
            BigDecimal qtyOrdered,
            BigDecimal unitCost,
            BigDecimal vatRate
    ) {}
}
