package za.co.handyflow.platform.facilities.dto;

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
 * Types and the fields they use ({@code targetId} is the work order or inspection):
 * WORK_ORDER_START; WORK_ORDER_HOLD (text = reason); WORK_ORDER_COMPLETE (text = completion notes, cost,
 * completedAt, downtimeMinutes, failureCause); INSPECTION_ANSWERS (answers); INSPECTION_COMPLETE (text = notes).
 */
public record SyncRequest(@NotEmpty @Size(max = 50) List<@Valid Operation> operations) {

    public record Operation(
            @NotNull UUID opId, @NotBlank String type, @NotNull UUID targetId, @Size(max = 2000) String text,
            BigDecimal cost, Instant completedAt, Integer downtimeMinutes, @Size(max = 500) String failureCause,
            @Size(max = 100) List<@Valid Answer> answers
    ) {}

    public record Answer(@NotNull UUID itemId, @NotBlank String outcome, @Size(max = 1000) String comment) {}
}
