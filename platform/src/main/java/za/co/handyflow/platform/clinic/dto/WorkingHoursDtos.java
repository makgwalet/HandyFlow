package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class WorkingHoursDtos {
    private WorkingHoursDtos() {}

    /** dayOfWeek 1 = Monday ... 7 = Sunday; from/to are "HH:mm" clinic time. */
    public record WindowDto(int dayOfWeek, String from, String to) {}

    /** The whole week. An empty list removes the restriction (the practitioner can be booked at any time). */
    public record SaveWorkingHoursRequest(@NotNull List<WindowDto> windows) {}
}
