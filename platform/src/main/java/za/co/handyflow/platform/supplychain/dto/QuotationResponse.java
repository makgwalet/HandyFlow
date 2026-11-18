package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuotationResponse(
        UUID id,
        UUID rfqId,
        UUID supplierId,
        String supplierName,
        String status,
        String statusReason,
        String quoteReference,
        Instant receivedAt,
        LocalDate validUntil,
        int leadTimeDays,
        Integer warrantyMonths,
        Integer paymentTermsDays,
        BigDecimal deliveryCost,
        BigDecimal otherCharges,
        BigDecimal subtotal,
        BigDecimal landedCost,
        BigDecimal totalInclVat,
        Integer technicalScore,
        String technicalNotes,
        String evaluatedByName,
        String notes,
        boolean hasFile,
        String fileName,
        String capturedByName,
        List<Line> lines
) {
    public record Line(
            UUID id,
            UUID rfqLineId,
            boolean noBid,
            BigDecimal qtyOffered,
            BigDecimal unitPrice,
            BigDecimal discountPct,
            BigDecimal netUnitPrice,
            BigDecimal vatRate,
            BigDecimal lineTotal
    ) {}
}
