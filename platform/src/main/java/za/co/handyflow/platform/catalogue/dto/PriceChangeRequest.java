package za.co.handyflow.platform.catalogue.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Propose a new price for one item on one list. No date means today. */
public record PriceChangeRequest(@NotNull UUID priceListId, @NotNull BigDecimal newPrice, LocalDate effectiveFrom, String reason) {}
