package za.co.handyflow.platform.clinic.dto.billing;

import java.math.BigDecimal;

public record RevenuePointResponse(
        String     period,
        int        consultations,
        BigDecimal grossBilled,
        BigDecimal schemePaid,
        BigDecimal patientPaid
) {}
