package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record UpsertSiteRequest(
        @NotBlank String name, String siteType, Map<String, String> address, String notes
) {}
