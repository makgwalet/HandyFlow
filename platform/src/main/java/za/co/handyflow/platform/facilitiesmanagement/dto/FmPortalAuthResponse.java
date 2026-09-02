package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.util.UUID;

/**
 * Per-module local DTO — confirmed convention (not a nonexistent
 * shared.PortalAuthResponse) across every sibling provider module's own
 * portal stack (TrainProvPortalAuthService's own PortalAuthResponse etc.).
 */
public record FmPortalAuthResponse(
        String token,
        UUID portalUserId,
        String email,
        String fullName
) {}
