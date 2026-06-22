package za.co.handyflow.platform.clinic.dto.billing;

import java.math.BigDecimal;
import java.util.UUID;

public record ClinicClaimLineResponse(
        UUID id,
        String lineType,
        String tariffCode,
        String nappiCode,
        String icd10Code,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal grossAmount,
        BigDecimal schemePortion,
        BigDecimal patientPortion
) {}