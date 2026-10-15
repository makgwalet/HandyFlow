package za.co.handyflow.platform.earthmoving.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AlertResponse(UUID id, String alertType, String severity, UUID assetId, String refType, UUID refId, String title,
                            String message, String status, int level, Instant createdAt, Instant escalatedAt,
                            UUID acknowledgedBy, Instant acknowledgedAt, Instant resolvedAt, String resolutionNote,
                            List<AlertDeliveryResponse> deliveries) {}
