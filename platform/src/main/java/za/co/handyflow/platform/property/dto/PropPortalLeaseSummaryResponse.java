package za.co.handyflow.platform.property.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

// FIX (Property tenant portal, agreed design): what a tenant sees for
// their own lease — grounded in fields that already existed on
// Lease/Unit/Property, no new entities needed for the view itself.
public record PropPortalLeaseSummaryResponse(
        UUID leaseId,
        String propertyName,
        Map<String, String> propertyAddress,
        String unitNumber,
        String lesseeName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal monthlyRent,
        BigDecimal depositAmount,
        boolean depositPaid,
        Integer paymentDay,
        BigDecimal escalationRate,
        String status
) {}
