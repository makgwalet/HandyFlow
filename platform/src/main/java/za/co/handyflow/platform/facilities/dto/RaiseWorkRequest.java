package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * A fault/maintenance request from a site user. Category defaults to CORRECTIVE (PPM cannot be requested);
 * priority is resolved by the priority rules; reportedBy defaults to the signed-in user's name.
 * Attach photos afterwards via the work order's evidence endpoint.
 */
public record RaiseWorkRequest(
        @NotNull UUID siteId, UUID assetId, @NotBlank String description,
        String category, String priority, boolean safetyImpact, String reportedBy
) {}