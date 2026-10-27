package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** One or more answers; resending an answer replaces the earlier one. */
public record InspectionAnswersRequest(@NotEmpty @Size(max = 100) List<@Valid Answer> answers) {
    public record Answer(@NotNull UUID itemId, @NotBlank String outcome, @Size(max = 1000) String comment) {}
}
