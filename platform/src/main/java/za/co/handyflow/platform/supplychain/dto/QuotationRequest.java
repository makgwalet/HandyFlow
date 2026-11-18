package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A supplier's quotation as captured by the buyer. A line is either priced or marked noBid. */
public record QuotationRequest(
        UUID supplierId,
        String quoteReference,
        LocalDate validUntil,
        Integer leadTimeDays,
        Integer warrantyMonths,
        Integer paymentTermsDays,
        BigDecimal deliveryCost,
        BigDecimal otherCharges,
        String notes,
        List<Line> lines
) {
    public record Line(
            UUID rfqLineId,
            Boolean noBid,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPct,
            BigDecimal vatRate
    ) {}
}
