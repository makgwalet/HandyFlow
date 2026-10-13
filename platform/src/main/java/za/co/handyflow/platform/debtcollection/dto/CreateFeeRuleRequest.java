package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.FeeCalcMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Creates a DRAFT (the next version if the code exists). FIXED needs fixedAmount; PERCENT_OF_OUTSTANDING needs
 * percentOfOutstanding (5 = 5%). legalBasisNote is mandatory: say why this fee is lawful and under which regime.
 */
public record CreateFeeRuleRequest(
        @NotBlank String code,
        @NotBlank String description,
        @NotNull FeeCalcMethod calcMethod,
        BigDecimal fixedAmount,
        BigDecimal percentOfOutstanding,
        BigDecimal maxPerCase,
        Integer maxChargesPerMonth,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotBlank String legalBasisNote
) {}
