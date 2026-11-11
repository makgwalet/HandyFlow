package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * phase: PRE_EVENT, SETUP, EVENT_DAY, BREAKDOWN, POST_EVENT or CONTINGENCY. A CONTINGENCY item is a
 * contingency plan: title = the scenario, description = the response, assignedTo = the owner, and
 * "done" = the plan has been reviewed. required defaults to true; sortOrder defaults to the end of the phase.
 */
public record ChecklistItemRequest(
        @NotBlank String phase,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String description,
        @Size(max = 200) String assignedTo,
        LocalDateTime dueAt,
        Boolean required,
        Integer sortOrder
) {}