package za.co.handyflow.platform.earthmoving.dto;

import java.time.Instant;
import java.util.UUID;

public record AlertDeliveryResponse(UUID userId, int level, String status, String detail, Instant sentAt) {}
