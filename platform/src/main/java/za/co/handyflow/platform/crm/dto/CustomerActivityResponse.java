package za.co.handyflow.platform.crm.dto;

import za.co.handyflow.platform.crm.domain.model.ActivityType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * CustomerActivityResponse — single timeline entry.
 *
 * WHY String for activityType and not the enum?
 * Serializing as String makes the JSON human-readable ("UPDATED" not "1").
 * Jackson maps enums to strings by default when the ObjectMapper is
 * configured with WRITE_ENUMS_USING_TO_STRING or the field is annotated.
 * We keep it as ActivityType here and let Jackson handle serialization.
 */
public record CustomerActivityResponse(
        UUID id,
        ActivityType activityType,
        Map<String, Object> payload,
        String note,
        UUID performedBy,
        Instant createdAt
) {}
