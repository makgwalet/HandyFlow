package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Phase 7d: overnight stays at the truck stop, owned by Fuel. */
public final class StayDtos {
    private StayDtos() {}

    public record SettingsRequest(BigDecimal nightlyRate, Integer bays) {}

    public record SettingsView(BigDecimal nightlyRate, Integer bays) {}

    public record BookRequest(@NotBlank @Size(max = 20) String vehicleRegistration, @Size(max = 120) String driverName, @Size(max = 40) String driverPhone,
                              @NotNull LocalDate arrivalDate, @NotNull LocalDate departureDate, @Size(max = 300) String note) {}

    public record DepartureRequest(@NotNull LocalDate departureDate) {}

    /** {@code date} is the day the truck left; empty means today. */
    public record CheckOutRequest(LocalDate date) {}

    public record CancelRequest(@NotBlank @Size(max = 300) String reason) {}

    /** nights and amount are for the planned dates until the truck has left, then for the actual departure. overstaying is true when still in the yard past the planned date. */
    public record StayView(UUID id, String vehicleRegistration, String driverName, String driverPhone, LocalDate arrivalDate, LocalDate departureDate,
                           LocalDate actualDepartureDate, String status, BigDecimal ratePerNight, int nights, BigDecimal amount, String note,
                           String cancelledReason, boolean overstaying, Instant checkedInAt, Instant checkedOutAt) {}

    /** The yard on one date. bays and free are null when no bay count is set. */
    public record Occupancy(LocalDate date, int inYard, int arrivals, int departures, Integer bays, Integer free) {}
}
