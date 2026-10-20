package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.AccidentStatus;

public record ChangeAccidentStatusRequest(
        @NotNull AccidentStatus status,
        @Size(max = 2000) String note
) {}
