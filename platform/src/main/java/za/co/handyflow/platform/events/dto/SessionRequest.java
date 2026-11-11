package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Times are wall-clock at the venue (South African time), the same convention as the event's own start and end. */
public record SessionRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String description,
        @NotBlank String sessionType,       // KEYNOTE, SESSION, BREAKOUT, PANEL, WORKSHOP, BREAK, MEAL, REGISTRATION, ENTERTAINMENT, SETUP, BREAKDOWN, OTHER
        @Size(max = 100) String room,
        @Size(max = 200) String speaker,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        @Size(max = 1000) String notes
) {}