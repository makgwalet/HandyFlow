package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Optional new validity for a re-sent link; omit to use the default. */
public record ReissueLinkRequest(
        @Min(value = 1, message = "Link expiry must be at least 1 day")
        @Max(value = 90, message = "Link expiry must not exceed 90 days") Integer expiryDays
) {}
