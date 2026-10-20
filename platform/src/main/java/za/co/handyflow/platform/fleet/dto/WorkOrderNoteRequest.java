package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.Size;

public record WorkOrderNoteRequest(
        @Size(max = 4000) String note
) {}