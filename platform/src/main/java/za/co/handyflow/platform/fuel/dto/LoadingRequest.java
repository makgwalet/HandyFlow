package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record LoadingRequest(
        @NotNull @Positive(message = "Loaded litres must be greater than zero") BigDecimal loadedLitres,
        @Size(max = 200) String sealNumbers
) {}
