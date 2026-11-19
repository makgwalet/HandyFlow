package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.EsgCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.EsgDirection;
import za.co.handyflow.platform.legalcompliance.domain.model.EsgFrequency;
import za.co.handyflow.platform.legalcompliance.domain.model.EsgMetricStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** ESG metrics and readings. Target status is worked out from the target that applied when each reading was recorded. */
public final class EsgDtos {

    private EsgDtos() {}

    public record MetricRequest(@NotBlank @Size(max = 255) String name, String description, @NotNull EsgCategory category, @NotBlank @Size(max = 40) String unit, @NotNull EsgFrequency frequency,
                                @NotNull EsgDirection direction, BigDecimal targetValue, @NotBlank @Size(max = 255) String ownerName, @Size(max = 100) String frameworkRef) {}

    public record ReadingRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd, @NotNull BigDecimal value, String notes) {}

    public record CorrectionRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd, @NotNull BigDecimal value, String notes, @NotBlank String reason) {}

    public record MetricResponse(UUID id, String reference, String name, String description, EsgCategory category, String unit, EsgFrequency frequency, EsgDirection direction, BigDecimal targetValue,
                                 String ownerName, String frameworkRef, EsgMetricStatus status, LocalDate latestPeriodEnd, BigDecimal latestValue, String latestTargetStatus,
                                 boolean readingOverdue, long readingCount, String retiredReason) {}

    public record ReadingResponse(UUID id, LocalDate periodStart, LocalDate periodEnd, BigDecimal value, String notes, BigDecimal targetSnapshot, EsgDirection directionSnapshot, String targetStatus,
                                  String recordedByName, UUID correctsId, boolean superseded, Instant supersededAt, String supersedeReason, Instant recordedAt) {}

    public record MetricDetail(MetricResponse metric, List<ReadingResponse> readings) {}

    public record EsgSummary(long active, long environmental, long social, long governance, long readingsOverdue, long targetsMissed, long noReadings) {}
}
