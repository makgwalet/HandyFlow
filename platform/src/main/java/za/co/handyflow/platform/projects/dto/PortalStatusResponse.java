package za.co.handyflow.platform.projects.dto;

import java.time.Instant;

/**
 * @param enabled    staff switch for portal access
 * @param accessible enabled AND not expired — i.e. does the link work right now
 * @param expiresAt  null = never expires
 * @param token      only populated by rotate-token (the one time a new link is issued)
 */
public record PortalStatusResponse(boolean enabled, boolean accessible, Instant expiresAt, String token) {}