package za.co.handyflow.platform.security.dto;

import java.time.Instant;

import java.time.LocalDate;
import java.util.UUID;

public record GuardScreeningResponse(
        UUID        id,
        UUID        guardId,
        String      guardName,
        String      screeningType,
        String      reason,
        String      result,
        String      conductedBy,
        LocalDate conductedAt,
        LocalDate   nextDueAt,
        Instant     createdAt
) {}