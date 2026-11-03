package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * forecastToComplete = ADDITIONAL cost still expected beyond what is already actual + committed.
 * null clears the manual estimate (the engine then assumes the unspent budget will be spent).
 */
public record SetForecastRequest(
        @DecimalMin(value = "0.00", message = "Forecast to complete cannot be negative")
        BigDecimal forecastToComplete
) {}