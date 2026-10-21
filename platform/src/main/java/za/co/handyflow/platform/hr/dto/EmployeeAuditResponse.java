package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.util.UUID;

public record EmployeeAuditResponse(UUID id, String action, String detail, UUID actorId, Instant createdAt) {}
