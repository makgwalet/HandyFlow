package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.AuditAction;
import za.co.handyflow.platform.crm.domain.model.AuditEntity;

/** actorId is null when the system did it (a scheduled job or automation). */
public record AuditEventResponse(UUID id, AuditEntity entityType, UUID entityId, String entityName, AuditAction action, String detail, UUID actorId, Instant createdAt) {}
