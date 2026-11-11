package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GuestResponse(
        UUID id,
        String ticketNumber,
        String qrCode,
        String fullName,
        String email,
        String phone,
        String company,
        String dietaryRequirements,
        UUID tierId,
        String tierName,
        String status,
        String paymentStatus,
        BigDecimal amountPaid,
        Instant checkedInAt,
        Instant createdAt,
        String source,      // STAFF | GROUP | WALK_IN | IMPORT | WAITLIST | TRANSFER  (new, additive)
        UUID groupRef       // shared by members of one group registration (new, additive)
) {}