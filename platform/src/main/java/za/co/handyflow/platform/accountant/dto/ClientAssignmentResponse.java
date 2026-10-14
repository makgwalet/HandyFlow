package za.co.handyflow.platform.accountant.dto;

import java.time.Instant;
import java.util.UUID;

/** A person assigned to a practice client (finance patch F15). */
public record ClientAssignmentResponse(UUID id, UUID clientId, UUID userId, String userName, String role, Instant assignedAt) {}
