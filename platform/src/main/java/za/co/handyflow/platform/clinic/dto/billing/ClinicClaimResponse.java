package za.co.handyflow.platform.clinic.dto.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// FIX: added patientName and practitionerName (denormalised for ClaimsTab display)
public record ClinicClaimResponse(
        UUID   id,
        UUID   consultationId,
        UUID   patientId,
        String patientName,        // batch-loaded in ClinicBillingService.getClaims()
        UUID   practitionerId,
        String practitionerName,   // batch-loaded in ClinicBillingService.getClaims()
        String status,
        String schemeName,
        String memberNumber,
        String dependentCode,
        BigDecimal grossAmount,
        BigDecimal schemePortion,
        BigDecimal patientPortion,
        Instant submittedAt,
        String referenceNumber,
        String rejectionReason,
        List<ClinicClaimLineResponse> lines,
        Instant createdAt
) {}
