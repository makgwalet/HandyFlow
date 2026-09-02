package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record CreateFmSiteRequest(
        @NotNull UUID clientId, @NotBlank String name, String siteType, Map<String, String> address, String notes
) {}
