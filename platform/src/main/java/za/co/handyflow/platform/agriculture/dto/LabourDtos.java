package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request and response shapes for costing labour (ADR-001, W3). Agriculture never returns a salary: only the hourly RATE worked out from it, and
 * only to callers who may see HR data.
 */
public final class LabourDtos {

    private LabourDtos() {}

    /** {@code configured} is false while no row exists and the defaults (45 hours, 0% on-cost) are in use. */
    public record FinanceSettingsResponse(BigDecimal standardHoursPerWeek, BigDecimal labourOnCostPercent, boolean configured) {}

    public record UpdateFinanceSettingsRequest(@NotNull BigDecimal standardHoursPerWeek, @NotNull BigDecimal labourOnCostPercent) {}

    /**
     * Work with labour hours that has not been costed. {@code suggestedRate} is the BASE hourly rate (before on-costs) worked out from HR; it is
     * null, with {@code rateNote} saying why, when there is no HR rate to offer. {@code estimatedCost} includes the on-cost.
     */
    public record LabourCandidate(
            String sourceType,              // INPUT_APPLICATION | HARVEST
            UUID sourceId, UUID cropCycleId, LocalDate date, String description, UUID workerId, String workerName,
            BigDecimal hours,
            BigDecimal suggestedRate, String rateSource, String rateNote, BigDecimal estimatedCost
    ) {}

    /** {@code hrRatesAvailable} is false when the caller may not see HR data, so no salary-based rates are offered or used. */
    public record LabourOverview(FinanceSettingsResponse settings, boolean hrRatesAvailable, List<LabourCandidate> candidates) {}

    /** {@code hourlyRate} is an optional typed-in BASE rate (before on-costs) that replaces the HR one, and is how casual workers are costed. */
    public record LabourItem(@NotBlank String sourceType, @NotNull UUID sourceId, @Positive BigDecimal hourlyRate) {}

    public record CostLabourRequest(@NotNull @Valid List<LabourItem> items) {}
}
