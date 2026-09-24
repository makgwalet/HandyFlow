package za.co.handyflow.platform.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/**
 * tenantId is a raw UUID, not a slugOrId string like most other admin
 * endpoints resolve — deliberately: by the time a support engineer is
 * viewing a specific tenant closely enough to trigger an action on it,
 * the admin console already has that tenant's real id in hand (from
 * whichever "load this tenant" call got them there), so there's no need
 * for this endpoint to duplicate AdminService's own slug-resolution
 * logic just to immediately turn it back into a UUID.
 */
public record ExecuteSupportActionRequest(
        @NotNull UUID tenantId, UUID targetId, Map<String, String> params
) {}
