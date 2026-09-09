package za.co.handyflow.platform.property.dto;

import java.util.UUID;

/**
 * Deliberately a local, per-module DTO, matching the same precedent
 * already established across every portal-enabled provider module in
 * this codebase (Warehousing, Training Provider, etc.) — there is no
 * shared.PortalAuthResponse to reuse.
 */
public record PortalAuthResponse(String token, UUID userId, String email, String fullName) {}
