package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record OrderDeliveryRequest(
        @NotNull @Positive(message = "Litres must be greater than zero") BigDecimal litres,
        @NotNull Instant scheduledAt,
        String driverName, String vehicleReg,
        Map<String, String> deliveryAddress
) {}
