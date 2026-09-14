package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddPlanEntryRequest(
        @NotNull UUID universeEntryId,
        UUID riskAssessmentId,           // optional — a plan entry can be added before a risk assessment exists, though the common case is selecting entries that already have one
        @Min(1) @Max(4) Integer plannedQuarter,
        String rationale
) {}
