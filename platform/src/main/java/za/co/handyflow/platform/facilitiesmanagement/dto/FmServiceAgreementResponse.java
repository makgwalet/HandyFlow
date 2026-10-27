package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** {@code siteIds} empty = all sites; {@code categories} empty = all categories. */
public record FmServiceAgreementResponse(
        UUID id, UUID clientId, String billingType, BigDecimal monthlyFee, BigDecimal hourlyRate,
        LocalDate startDate, LocalDate endDate, String status, Instant createdAt,
        String name, BigDecimal partsMarkupPercent, List<UUID> siteIds, List<String> categories,
        List<FmSlaTargetDto> slaTargets
) {}