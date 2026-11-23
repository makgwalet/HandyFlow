package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Shared by /increase-count and /reduce-count; the direction is the endpoint. A reason is required (at least three characters)
 * and is kept in the group's count log. For a sale or a death use a movement or mortality record instead, so the head count and
 * the record agree.
 */
public record AdjustGroupCountRequest(@NotNull @Positive Integer count, @NotBlank String reason) {}
