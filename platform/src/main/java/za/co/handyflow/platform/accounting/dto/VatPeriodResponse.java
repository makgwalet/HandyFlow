package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VatPeriodResponse(
        UUID id,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,          // OPEN | CLOSED | SUBMITTED
        BigDecimal outputVat,
        BigDecimal inputVat,
        BigDecimal vatPayable   // computed: output - input
) {}