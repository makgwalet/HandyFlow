package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * "We're running 20 minutes late": move every not-yet-started session from a point onward by the same
 * amount. Positive = later, negative = earlier. fromSessionId is the first session to move; leave it out
 * to move everything that hasn't started yet and is still to come from now. Sessions already started or
 * finished are never moved.
 */
public record ShiftSessionsRequest(
        @NotNull @Min(-120) @Max(240) Integer minutes,
        UUID fromSessionId
) {}