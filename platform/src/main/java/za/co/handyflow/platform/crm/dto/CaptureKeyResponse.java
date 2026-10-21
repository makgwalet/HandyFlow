package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.LeadSource;

public record CaptureKeyResponse(UUID id, String name, String keyPrefix, LeadSource defaultSource, Instant createdAt, Instant lastUsedAt, boolean active) {}
