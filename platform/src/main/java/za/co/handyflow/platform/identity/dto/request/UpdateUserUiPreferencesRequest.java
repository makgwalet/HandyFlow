package za.co.handyflow.platform.identity.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Full replacement of the current user's overrides. Null (or omitted) in any
 * field means "inherit the tenant default".
 */
public record UpdateUserUiPreferencesRequest(
        String theme,
        String sidebar,
        String container,
        String brandColor,
        @Size(max = 12, message = "You can pin at most 12 modules")
        List<@Pattern(regexp = "^[a-z][a-z0-9-]{0,39}$", message = "Invalid module key") String> pinnedModules
) {}
