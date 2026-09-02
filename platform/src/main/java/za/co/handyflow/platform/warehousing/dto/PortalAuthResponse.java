package za.co.handyflow.platform.warehousing.dto;

import java.util.UUID;

/**
 * Deliberately a local, per-module DTO (not a shared.PortalAuthResponse —
 * that class does not exist; confirmed via the same real compile-error
 * precedent already established on the Auditor/Collections Agency
 * modules). Every portal-enabled provider module defines its own.
 */
public record PortalAuthResponse(String token, UUID userId, String email, String fullName) {}
