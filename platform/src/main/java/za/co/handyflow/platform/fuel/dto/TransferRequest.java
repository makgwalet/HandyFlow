package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull UUID fromTankId,
        @NotNull UUID toTankId,
        @NotNull @Positive(message = "Litres must be greater than zero") BigDecimal litres,
        @NotBlank @Size(min = 5, max = 500, message = "Reason must be 5 to 500 characters") String reason
) {}
