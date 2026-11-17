package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Phase 7d: overnight stays at the truck stop, owned by Fuel. */
public final class StayDtos {
    private StayDtos() {}

    public record SettingsRequest(BigDecimal nightlyRate, Integer bays) {}

    public record SettingsView(BigDecimal nightlyRate, Integer bays) {}

    public record BookRequest(@NotBlank @Size(max = 20) String vehicleRegistration, @Size(max = 120) String driverName, @Size(max = 40) String driverPhone,
                              @NotNull LocalDate arrivalDate, @NotNull LocalDate departureDate, @Size(max = 300) String note, UUID accountId, UUID fleetVehicleId) {
        /** Without a Fleet vehicle: a registration that matches one is still linked automatically. */
        public BookRequest(String vehicleRegistration, String driverName, String driverPhone, LocalDate arrivalDate, LocalDate departureDate, String note, UUID accountId) {
            this(vehicleRegistration, driverName, driverPhone, arrivalDate, departureDate, note, accountId, null);
        }
    }

    /** Charge the stay to this Fuel customer account; null means paid at the gate. */
    public record AccountRequest(UUID accountId) {}

    public record DepartureRequest(@NotNull LocalDate departureDate) {}

    /** {@code date} is the day the truck left; empty means today. */
    public record CheckOutRequest(LocalDate date) {}

    public record CancelRequest(@NotBlank @Size(max = 300) String reason) {}

    /** nights and amount are for the planned dates until the truck has left, then for the actual departure. overstaying is true when still in the yard past the planned date. */
    public record StayView(UUID id, String vehicleRegistration, String driverName, String driverPhone, LocalDate arrivalDate, LocalDate departureDate,
                           LocalDate actualDepartureDate, String status, BigDecimal ratePerNight, int nights, BigDecimal amount, String note,
                           String cancelledReason, boolean overstaying, Instant checkedInAt, Instant checkedOutAt,
                           UUID accountId, String invoiceState, String invoiceNumber, String invoiceError, UUID bayId, UUID fleetVehicleId) {
        /** Before Fleet links existed. */
        public StayView(UUID id, String vehicleRegistration, String driverName, String driverPhone, LocalDate arrivalDate, LocalDate departureDate,
                        LocalDate actualDepartureDate, String status, BigDecimal ratePerNight, int nights, BigDecimal amount, String note,
                        String cancelledReason, boolean overstaying, Instant checkedInAt, Instant checkedOutAt,
                        UUID accountId, String invoiceState, String invoiceNumber, String invoiceError, UUID bayId) {
            this(id, vehicleRegistration, driverName, driverPhone, arrivalDate, departureDate, actualDepartureDate, status, ratePerNight, nights, amount, note,
                    cancelledReason, overstaying, checkedInAt, checkedOutAt, accountId, invoiceState, invoiceNumber, invoiceError, bayId, null);
        }
        public StayView(UUID id, String vehicleRegistration, String driverName, String driverPhone, LocalDate arrivalDate, LocalDate departureDate,
                        LocalDate actualDepartureDate, String status, BigDecimal ratePerNight, int nights, BigDecimal amount, String note,
                        String cancelledReason, boolean overstaying, Instant checkedInAt, Instant checkedOutAt,
                        UUID accountId, String invoiceState, String invoiceNumber, String invoiceError) {
            this(id, vehicleRegistration, driverName, driverPhone, arrivalDate, departureDate, actualDepartureDate, status, ratePerNight, nights, amount, note,
                    cancelledReason, overstaying, checkedInAt, checkedOutAt, accountId, invoiceState, invoiceNumber, invoiceError, null, null);
        }
    }

    public record BayRequest(@NotBlank @Size(max = 40) String name) {}

    public record BayActiveRequest(boolean active) {}

    /** bayId null removes the truck from its bay. */
    public record BayAssignRequest(UUID bayId) {}

    public record BayView(UUID id, String name, boolean active) {}

    /** One night in the capacity view. free is null when no bay count is set. */
    public record NightView(LocalDate date, int used, int arrivals, int departures, Integer free) {}

    /** A truck still in the yard past its planned departure. */
    public record OverstayView(UUID id, String vehicleRegistration, LocalDate plannedDeparture, long daysOver) {}

    /** Bays used and free night by night, from {@code from} for {@code nights.size()} nights, and the trucks that are overstaying. */
    public record CapacityView(LocalDate from, Integer bays, List<NightView> nights, List<OverstayView> overstays) {}

    /** The yard on one date. bays and free are null when no bay count is set. */
    public record Occupancy(LocalDate date, int inYard, int arrivals, int departures, Integer bays, Integer free) {}
}
