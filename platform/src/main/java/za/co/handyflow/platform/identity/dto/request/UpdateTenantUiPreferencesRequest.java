package za.co.handyflow.platform.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Full replacement of tenant appearance defaults. Enum values are accepted
 * as strings and parsed in the service, so a bad value returns a 400 with
 * the allowed values rather than a generic JSON parse failure.
 */
public record UpdateTenantUiPreferencesRequest(
        @NotBlank String brandColor,
        @NotNull Boolean brandLocked,
        @NotBlank String defaultTheme,
        @NotBlank String defaultSidebar,
        @NotBlank String defaultContainer
) {}
