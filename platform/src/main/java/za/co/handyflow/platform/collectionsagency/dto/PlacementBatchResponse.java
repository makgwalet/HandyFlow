package za.co.handyflow.platform.collectionsagency.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PlacementBatchResponse(
        UUID id, UUID clientId, String batchReference, LocalDate placedDate, int totalAccounts,
        BigDecimal totalPlacedValue, Instant acknowledgedAt, UUID acknowledgedBy, String notes
) {}
