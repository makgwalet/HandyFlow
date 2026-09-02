package za.co.handyflow.platform.collectionsagency.dto;

import java.time.Instant;
import java.util.UUID;

/** Direct mirror of every sibling provider module's own PortalAccessGrantResponse. */
public record PortalAccessGrantResponse(
        UUID id, String inviteEmail, String status, Instant invitedAt, Instant acceptedAt, Instant revokedAt
) {}
