package za.co.handyflow.platform.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateColumnRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 20) String color,
        int sortOrder,
        boolean isDoneColumn,
        /** Optional. When omitted the column keeps its category (or derives it from isDoneColumn). */
        @Pattern(regexp = "TODO|IN_PROGRESS|IN_REVIEW|BLOCKED|DONE") String category) {}
