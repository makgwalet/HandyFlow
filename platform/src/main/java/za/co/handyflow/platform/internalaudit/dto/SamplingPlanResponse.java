package za.co.handyflow.platform.internalaudit.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SamplingPlanResponse(
        UUID id, UUID engagementId,
        int population, BigDecimal populationValue, String samplingObjective, String samplingMethod,
        String riskLevel, BigDecimal confidenceLevel, BigDecimal expectedErrorRate, BigDecimal tolerableErrorRate,
        int sampleSize, String selectionMethod, LocalDate samplePeriodFrom, LocalDate samplePeriodTo,
        String exclusions, String rationale, UUID preparedBy, UUID reviewedBy, String status, Instant createdAt,
        List<SampleItemResponse> items
) {}
