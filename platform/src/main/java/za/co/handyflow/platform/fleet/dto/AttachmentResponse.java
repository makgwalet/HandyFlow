package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        String name,
        String category,
        String serialNumber,
        String status,
        UUID vehicleId,
        String registration,
        Instant attachedAt,
        BigDecimal totalHoursInUse       // lifetime, including the current stay
) {}