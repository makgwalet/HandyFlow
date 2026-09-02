package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordPaymentRequest(
        @NotNull BigDecimal amount
) {}
