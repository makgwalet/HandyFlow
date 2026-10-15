package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** Switch a machine between hours and kilometres. All three numbers are given again in the new unit. */
public record ChangeMeterRequest(@NotBlank String meterType, BigDecimal currentReading, BigDecimal lastServiceReading,
                                 BigDecimal serviceInterval, String reason) {}
