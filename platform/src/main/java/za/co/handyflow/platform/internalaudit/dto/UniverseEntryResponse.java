package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UniverseEntryResponse(
        UUID id, String name, String description, String processArea, String glAccountGroup,
        LocalDate lastAuditDate, boolean active, Instant createdAt,
        // Convenience: the latest risk assessment's finalAuditRisk, if
        // one exists — null when a universe entry has never been
        // assessed. Saves the frontend a second round trip per entry
        // when just listing the universe with its current risk level.
        String currentRiskLevel
) {}
