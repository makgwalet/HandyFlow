package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record MarkPlacedRequest(@NotNull BigDecimal offeredSalary) {}