package za.co.handyflow.platform.bookingagency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.UUID;

public record CreateResourceRequest(
        @NotNull UUID clientId,
        @NotBlank String name,
        String roleDescription,
        LocalTime workingHoursStart,
        LocalTime workingHoursEnd
) {}