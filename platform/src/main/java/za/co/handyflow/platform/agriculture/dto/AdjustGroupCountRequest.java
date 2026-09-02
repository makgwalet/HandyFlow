package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Shared by both /increase-count and /reduce-count endpoints — the
 * direction is determined by which endpoint is called, matching
 * {@link za.co.handyflow.platform.agriculture.domain.model.AgGroup}'s own
 * two separate methods ({@code increaseCount}/{@code reduceCount}), both of
 * which take a single positive int.
 */
public record AdjustGroupCountRequest(@NotNull @Positive Integer count) {}
