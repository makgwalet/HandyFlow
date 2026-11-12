package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** minutes = how early or late a guard may start a shift and still be linked to it. null = back to the platform default. */
public record SetClockInWindowRequest(@Min(5) @Max(480) Integer minutes) {}
