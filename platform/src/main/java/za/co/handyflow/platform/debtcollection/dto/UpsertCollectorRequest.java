package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** The user id is in the URL. tier defaults to STANDARD; skills are case-insensitive free-text tags (max 20); awayUntil = last day of leave, inclusive. */
public record UpsertCollectorRequest(
        @NotBlank String userName,
        String team,
        CollectorTier tier,
        Set<String> skills,
        @PositiveOrZero Integer maxOpenCases,
        @Positive BigDecimal maxCaseValue,
        @Positive BigDecimal maxPortfolioValue,
        LocalDate awayUntil
) {}
