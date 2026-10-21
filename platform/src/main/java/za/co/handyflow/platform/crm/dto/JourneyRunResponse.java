package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.UUID;

public record JourneyRunResponse(UUID id, UUID customerId, String customerName, int step, String outcome, String detail, Instant ranAt) {}
