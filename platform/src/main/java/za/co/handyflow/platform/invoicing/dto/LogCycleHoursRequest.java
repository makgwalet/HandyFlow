package za.co.handyflow.platform.invoicing.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record LogCycleHoursRequest(
        /** Actual hours the machine worked this cycle */
        @NotNull @DecimalMin("0")
        BigDecimal actualHours,

        /**
         * Period this log covers — used as invoice description.
         * e.g. "June 2026"
         */
        @NotBlank String periodLabel,

        /** Optional operator notes logged alongside hours */
        String operatorNotes,

        /**
         * Override the normal nextRunAt date if needed.
         * Null = use schedule's nextRunAt as the invoice date.
         */
        LocalDate invoiceDate
) {}