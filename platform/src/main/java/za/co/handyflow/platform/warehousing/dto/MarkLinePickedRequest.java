package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record MarkLinePickedRequest(@NotNull @Positive BigDecimal qty) {}
