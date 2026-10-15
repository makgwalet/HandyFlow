package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CompleteWorkOrderRequest(@NotBlank String workDone, String testResult, BigDecimal meterHours,
                                       BigDecimal downtimeHours, BigDecimal labourHours) {}
