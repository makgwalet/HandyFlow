package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A batch of things done on a device while offline. Each operation carries an {@code opId} the device invents
 * once; resending the same opId never applies it twice.
 * <p>
 * Types and the fields they use ({@code targetId} is the work order):
 * WORK_ORDER_START; WORK_ORDER_HOLD (text = reason; never pauses the SLA clock from a device);
 * WORK_ORDER_COMPLETE (text = completion notes, cost, completedAt, downtimeMinutes, failureCause);
 * COST_LINE_ADD (costType, text = description, quantity, unitCost; pricing fields are not accepted from devices).
 */
public record FmSyncRequest(@NotEmpty @Size(max = 50) List<@Valid Operation> operations) {

    public record Operation(
            @NotNull UUID opId, @NotBlank String type, @NotNull UUID targetId, @Size(max = 2000) String text,
            BigDecimal cost, Instant completedAt, Integer downtimeMinutes, @Size(max = 500) String failureCause,
            @Size(max = 30) String costType, BigDecimal quantity, BigDecimal unitCost
    ) {}
}
