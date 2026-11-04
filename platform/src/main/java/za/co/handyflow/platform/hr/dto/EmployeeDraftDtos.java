package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public final class EmployeeDraftDtos {
    private EmployeeDraftDtos() {}

    /** {@code payload} is the registration form as JSON text, exactly as the page holds it. */
    public record Save(@NotBlank String payload, @Min(0) @Max(5) int step, String firstName, String lastName) {}

    /** The list: no payload, so a list of drafts does not carry everyone's personal details. */
    public record Summary(UUID id, String label, int step, Instant updatedAt) {}

    public record Detail(UUID id, String label, int step, String payload, Instant updatedAt) {}
}
