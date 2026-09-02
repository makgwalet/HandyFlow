package za.co.handyflow.platform.collectionsagency.dto;

import java.util.UUID;

/**
 * Deliberately a local, per-module DTO (not a shared.PortalAuthResponse —
 * that class does not exist; confirmed directly by an earlier compile
 * error on the Auditor module's equivalent). Every portal-enabled
 * provider module defines its own, matching the frontend's own
 * per-portal interface convention.
 */
public record PortalAuthResponse(String token, UUID userId, String email, String fullName) {}
