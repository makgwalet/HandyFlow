package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Map;

public final class PushDtos {
    private PushDtos() {}

    /** The driver is told graceMinutes after a load's scheduled time; dispatch escalationMinutes after that if it is still open. */
    public record OverduePolicy(@Min(15) @Max(1440) int graceMinutes, @Min(15) @Max(1440) int escalationMinutes) {}

    /** A person's own switches, by event type. A type that is not listed stays as it was (on by default). */
    public record Preferences(Map<String, Boolean> enabled) {}
}
