package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.util.UUID;

public record PayRunEventResponse(UUID id, String action, String fromStatus, String toStatus, UUID actorId, String note, Instant at) {}
