package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public final class ClosureDtos {
    private ClosureDtos() {}

    /** Whole days in clinic time, both ends inclusive. */
    public record CreateClosureRequest(@NotNull LocalDate firstDay, @NotNull LocalDate lastDay, String reason) {}

    public record ClosureResponse(UUID id, LocalDate firstDay, LocalDate lastDay, String reason) {}
}
