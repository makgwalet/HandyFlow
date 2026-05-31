package za.co.handyflow.platform.property.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RenewLeaseRequest(
        @NotNull LocalDate  newEndDate,
                 BigDecimal newMonthlyRent,
                 BigDecimal newEscalationRate
) {}
