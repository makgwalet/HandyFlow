package za.co.handyflow.platform.supplychain.dto;

import java.util.UUID;

/**
 * Percentages are null when there is no data for that measure. lowConfidence = fewer than 5
 * measured deliveries, so treat the score as indicative.
 */
public record SupplierScorecardResponse(
        UUID supplierId,
        String supplierName,
        String supplierStatus,
        int windowMonths,
        int deliveriesOnTime,
        int deliveriesLate,
        Double onTimePct,
        Double fillRatePct,
        Double rejectionPct,
        Double invoiceAccuracyPct,
        int invoicesChecked,
        int invoicesWithSupplierErrors,
        Double avgLeadTimeDays,
        Integer score,
        String rating,
        boolean lowConfidence,
        String complianceStatus
) {}
