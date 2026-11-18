package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Side-by-side comparison of an RFQ's quotations with weighted scores and the recommendation. */
public record RfqComparisonResponse(
        UUID rfqId,
        String rfqNumber,
        String status,
        RfqResponse.Weights weights,
        BigDecimal estimatedTotal,
        UUID recommendedQuotationId,
        UUID lowestPriceQuotationId,
        List<Row> rows
) {
    public record Row(
            UUID quotationId,
            UUID supplierId,
            String supplierName,
            String status,
            BigDecimal landedCost,
            BigDecimal totalInclVat,
            int leadTimeDays,
            Integer warrantyMonths,
            Integer paymentTermsDays,
            LocalDate validUntil,
            boolean complete,
            boolean expired,
            boolean ranked,
            Integer rank,
            BigDecimal priceScore,
            BigDecimal leadTimeScore,
            BigDecimal technicalScore,
            BigDecimal performanceScore,
            BigDecimal bbbeeScore,
            BigDecimal totalScore,
            BigDecimal estimatedSaving,
            List<String> flags
    ) {}
}
