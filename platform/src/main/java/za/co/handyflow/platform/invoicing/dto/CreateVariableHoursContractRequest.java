package za.co.handyflow.platform.invoicing.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateVariableHoursContractRequest(
        // Either customerId OR walkinClientName
        UUID customerId,

        @NotBlank @Size(max = 255)
        String title,

        String notes,

        /** MONTHLY is typical for mining contracts */
        @NotNull String frequency,

        @NotNull @DecimalMin("0.01")
        BigDecimal ratePerHour,

        /**
         * Minimum hours billed per cycle even if the machine worked less.
         * e.g. 150h minimum on a 200h/month machine.
         * Null = no minimum, bill exactly what was worked.
         */
        BigDecimal minimumHoursPerCycle,

        @DecimalMin("0") @DecimalMax("100")
        BigDecimal hoursVatRate,            // defaults to 15 if null

        /** When the contract kicks off — first invoice date */
        @NotNull Instant contractStartDate,

        /** 12 months out for a typical mining contract */
        @NotNull Instant contractEndDate,

        /** Total hours across the full contract term (informational) */
        BigDecimal contractedTotalHours,

        @Size(max = 255) String walkinClientName,
        String walkinClientEmail,
        @Size(max = 50)  String walkinClientPhone
) {}