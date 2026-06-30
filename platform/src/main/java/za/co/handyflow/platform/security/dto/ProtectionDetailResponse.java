package za.co.handyflow.platform.security.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProtectionDetailResponse(
        UUID    id,
        UUID    principalId,
        String  principalCodename,
        String  detailType,
        Instant startAt,
        Instant endAt,
        String  status,
        BigDecimal billingRate,
        String  clientReference,
        String  notes,
        int     teamSize,
        Instant createdAt
) {}
