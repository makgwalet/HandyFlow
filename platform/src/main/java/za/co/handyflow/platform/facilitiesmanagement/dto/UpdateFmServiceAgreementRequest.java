package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code endDate} replaces the stored value (null = open-ended). Every other field is "null = leave unchanged"
 * (so a partial update can't wipe the rate card): an EMPTY {@code siteIds} means all sites, an EMPTY
 * {@code categories} means all categories, and an EMPTY {@code slaTargets} removes all SLA targets.
 * Changes apply to work raised from now on — existing work orders keep the terms they were raised under.
 */
public record UpdateFmServiceAgreementRequest(
        BigDecimal monthlyFee, BigDecimal hourlyRate, LocalDate endDate,
        String name, BigDecimal partsMarkupPercent,
        Set<UUID> siteIds, Set<String> categories, @Valid List<FmSlaTargetDto> slaTargets
) {}