package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.BatteryRemovalReason;
import za.co.handyflow.platform.fleet.domain.model.BatteryStatus;

import java.time.Instant;
import java.util.UUID;

/** {@code outcome} is IN_STOCK, FAULTY (awaiting warranty claim / disposal) or SCRAPPED; SCRAPPED needs a {@code scrapReason}. */
public record RemoveBatteryRequest(
        @NotNull BatteryStatus outcome,
        @NotNull BatteryRemovalReason reason,
        @PositiveOrZero Integer odometer,
        Instant removedAt,
        @Size(max = 500) String scrapReason,
        UUID workOrderId
) {}