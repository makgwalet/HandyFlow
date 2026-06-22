package za.co.handyflow.platform.invoicing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LogHoursRequest(
        @NotNull @DecimalMin("0.01")
        BigDecimal hours,

        String note   // optional — e.g. "Dozer on site 07:00–13:00"
) {}
