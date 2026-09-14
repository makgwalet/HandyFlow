package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

// samplingMethod is deliberately NOT here -- fixed to AUDITOR_JUDGMENT
// server-side for V1, per the agreed design. population is calculated
// server-side from the actual posted-journal-entry count in the given
// period, not supplied by the caller -- the one input that's genuinely
// derivable from real data.
public record CreateSamplingPlanRequest(
        String samplingObjective,
        String riskLevel,
        BigDecimal expectedErrorRate,
        BigDecimal tolerableErrorRate,
        @NotNull @Min(1) Integer sampleSize,
        String selectionMethod, // RANDOM | SYSTEMATIC | JUDGMENTAL
        @NotNull LocalDate samplePeriodFrom,
        @NotNull LocalDate samplePeriodTo,
        String exclusions,
        String rationale
) {}
