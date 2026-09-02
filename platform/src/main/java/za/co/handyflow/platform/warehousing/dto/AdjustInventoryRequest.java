package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** delta may be negative (e.g. a damage write-off) or positive (a count correction upward) — see WhseInventory.adjustOnHand()'s own Javadoc for the guard against going below zero. */
public record AdjustInventoryRequest(@NotNull BigDecimal delta, @NotBlank String reason) {}
