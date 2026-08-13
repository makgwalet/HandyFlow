package za.co.handyflow.platform.recruitmentagency.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AgencyClientResponse(
        UUID id,
        String tradingName,
        String registrationNumber,
        String industry,
        BigDecimal placementFeePct,
        BigDecimal effectivePlacementFeePct, // resolved: client override, or agency default if null
        Integer guaranteePeriodDays,
        String contactName,
        String contactEmail,
        String contactPhone,
        LocalDate onboardedAt,
        String status,
        String notes,
        Instant createdAt
) {}