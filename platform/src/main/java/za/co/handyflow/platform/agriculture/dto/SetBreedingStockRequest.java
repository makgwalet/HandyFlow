package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;

/** Flags or unflags an animal as breeding stock. Required, so a missing value is refused rather than read as "not breeding stock". */
public record SetBreedingStockRequest(@NotNull Boolean breedingStock) {}
