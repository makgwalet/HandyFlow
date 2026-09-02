package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WriteOffCaseRequest(@NotNull @Positive BigDecimal amount, String reason) {}
