package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.InspectionOutcome;

import java.util.UUID;

public record InspectionAnswerRequest(
        @NotNull UUID resultId,
        @NotNull InspectionOutcome outcome,   // PASS | FAIL | NOT_APPLICABLE
        @Size(max = 2000) String comment
) {}