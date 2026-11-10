package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** Moves unallocated stock at one location between statuses (AVAILABLE, QUARANTINE, DAMAGED). */
public record ChangeStockStatusRequest(@NotNull UUID itemId, @NotNull UUID locationId,
                                       @NotBlank String fromStatus, @NotBlank String toStatus,
                                       @NotNull @Positive BigDecimal qty, @NotBlank String reason,
                                       /** Required for a lot-tracked item, ignored otherwise. */ UUID lotId) {}
