package za.co.handyflow.platform.trainingprovider.dto;

import java.util.UUID;

/** Per-module local DTO — confirmed convention (not a nonexistent shared.PortalAuthResponse) across every sibling provider module's own portal stack. */
public record PortalAuthResponse(
        String token,
        UUID portalUserId,
        String email,
        String fullName
) {}
