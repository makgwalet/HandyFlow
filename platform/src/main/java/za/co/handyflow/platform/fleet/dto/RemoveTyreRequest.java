package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.TyreRemovalReason;
import za.co.handyflow.platform.fleet.domain.model.TyreStatus;

import java.time.Instant;
import java.util.UUID;

/** {@code outcome} is IN_STOCK, AWAITING_RETREAD or SCRAPPED; SCRAPPED needs a {@code scrapReason}. */
public record RemoveTyreRequest(
        @NotNull TyreStatus outcome,
        @NotNull TyreRemovalReason reason,
        @PositiveOrZero Integer odometer,
        Instant removedAt,
        @Size(max = 500) String scrapReason,
        UUID workOrderId
) {}