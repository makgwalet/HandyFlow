package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AssignIncidentRequest(
        @NotBlank @Size(max = 200) String assigneeName,
        UUID assigneeUserId
) {}