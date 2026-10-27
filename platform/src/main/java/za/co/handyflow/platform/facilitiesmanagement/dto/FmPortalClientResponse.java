package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.util.UUID;

/** A client organisation a portal user has access to (a person may act for several). */
public record FmPortalClientResponse(UUID id, String name) {}
