package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record DuplicateEventRequest(
        @NotNull LocalDateTime newStartDatetime,
        @Size(max = 200) String title   // blank/null -> "Copy of <original title>"
) {}