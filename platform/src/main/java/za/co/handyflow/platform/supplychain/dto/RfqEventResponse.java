package za.co.handyflow.platform.supplychain.dto;

import java.time.Instant;
import java.util.UUID;

public record RfqEventResponse(UUID id, String eventType, String detail, String actorName, Instant createdAt) {}
