package za.co.handyflow.platform.legalpractice.dto;

import java.util.UUID;

/**
 * Local to this module — matches the confirmed per-module DTO convention
 * (AuditorPortalAuthResponse et al.): there is no shared.PortalAuthResponse.
 * Replaces the earlier {@code PortalAuthResponse} of the same shape; renamed
 * only for a clearer, less generic name now this module has a dedicated
 * dto package of its own auth types.
 */
public record LpPortalAuthResponse(String token, UUID portalUserId, String email, String fullName) {}
