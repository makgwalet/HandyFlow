package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * One SLA commitment: for work of this priority (LOW, NORMAL, HIGH, URGENT, EMERGENCY), respond within
 * {@code responseMinutes} and resolve within {@code resolutionMinutes}. {@code clock} is 24X7 (calendar time) or
 * BUSINESS_HOURS (counts only working hours on the tenant's business calendar). Resolution cannot be shorter than response.
 */
public record FmSlaTargetDto(
        @NotBlank String priority, @Min(1) int responseMinutes, @Min(1) int resolutionMinutes, @NotBlank String clock
) {}