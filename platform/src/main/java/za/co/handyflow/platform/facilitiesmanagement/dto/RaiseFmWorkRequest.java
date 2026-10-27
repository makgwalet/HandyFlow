package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * A fault/maintenance request logged by staff for a client's site. Category defaults to CORRECTIVE (PPM
 * cannot be requested); priority is resolved by the priority rules; reportedBy defaults to the signed-in
 * user's name. Attach photos afterwards via the work order's evidence endpoint.
 */
public record RaiseFmWorkRequest(
        @NotNull UUID clientId, @NotNull UUID siteId, UUID assetId, @NotBlank String description,
        String category, String priority, boolean safetyImpact, String reportedBy
) {}