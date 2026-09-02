package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordFmPaymentRequest(@NotNull BigDecimal amount) {}
