package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** ttlDays: null = platform default; 0 = never expires; otherwise 1–730 days. */
public record PortalTokenRequest(
        @Min(value = 0, message = "ttlDays cannot be negative")
        @Max(value = 730, message = "ttlDays cannot exceed 730")
        Integer ttlDays
) {}