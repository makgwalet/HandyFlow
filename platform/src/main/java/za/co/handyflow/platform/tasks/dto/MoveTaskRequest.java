package za.co.handyflow.platform.tasks.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Move a task to a column. sortOrder is the target position in that column (0 = top). */
public record MoveTaskRequest(
        @NotNull UUID columnId,
        @Min(0) int sortOrder) {}
