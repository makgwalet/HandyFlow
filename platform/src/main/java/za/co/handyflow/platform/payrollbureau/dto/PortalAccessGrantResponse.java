package za.co.handyflow.platform.payrollbureau.dto;

import java.time.Instant;
import java.util.UUID;

public record PortalAccessGrantResponse(
        UUID id, String inviteEmail, String status, Instant invitedAt, Instant acceptedAt, Instant revokedAt
) {}