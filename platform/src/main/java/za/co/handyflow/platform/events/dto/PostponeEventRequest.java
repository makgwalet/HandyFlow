package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PostponeEventRequest(
        @NotNull LocalDateTime newStartDatetime,
        @NotNull LocalDateTime newEndDatetime,
        @NotBlank @Size(max = 500) String reason,
        boolean notifyGuests
) {}