package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AddWorkOrderLineRequest(@NotBlank String kind, @NotBlank String description, @NotNull BigDecimal quantity,
                                      @NotNull BigDecimal unitCost, String supplier) {}
