package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A fault a client reports through the portal. The client says where it is, what is wrong, whether it is a safety
 * issue and (optionally) whether it is an emergency; priority is never client-set — staff confirm it on approval.
 * Category may be CORRECTIVE (default), EMERGENCY or OTHER.
 */
public record FmPortalRaiseRequest(
        @NotNull UUID siteId, UUID assetId,
        @NotBlank @Size(min = 10, max = 2000) String description,
        String category, boolean safetyImpact,
        @Size(max = 100) String contactName
) {}
