package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** One rate of a hire contract. Included kilometres apply to the kilometre rate only. */
public record ContractRateRequest(@NotBlank String basis, @NotNull BigDecimal unitRate, BigDecimal includedKm) {}
