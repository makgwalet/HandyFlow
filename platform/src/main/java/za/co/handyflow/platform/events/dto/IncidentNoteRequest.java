package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidentNoteRequest(
        @NotBlank @Size(max = 2000) String note
) {}