package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class TruckStopDtos {
    private TruckStopDtos() {}

    public record OfferingRequest(@NotNull UUID bookingsServiceId, @NotBlank String kind, UUID depotId) {}
    public record OfferingStatusRequest(boolean active) {}
    public record OfferingResponse(UUID id, UUID bookingsServiceId, String serviceName, String kind, UUID depotId, boolean active,
                                   int durationMinutes, BigDecimal price, List<Resource> resources, boolean serviceMissing) {}
    public record Resource(UUID id, String name) {}
    /** A Bookings service not yet offered, for the picker. */
    public record ServiceOption(UUID id, String name, int durationMinutes, BigDecimal price) {}
    public record Slot(LocalTime start, LocalTime end, String label) {}

    public record ReserveRequest(@NotNull UUID offeringId, UUID resourceId, @NotNull LocalDate date, @NotNull LocalTime start,
                                 @NotBlank @Size(max = 20) String vehicleRegistration,
                                 @Size(max = 120) String driverName, @Size(max = 40) String driverPhone, @Size(max = 200) String notes) {}
    public record OutcomeRequest(@Size(max = 300) String reason) {}

    public record ReservationResponse(UUID id, UUID offeringId, String kind, String serviceName, UUID bookingId, String bookingNumber,
                                      String vehicleRegistration, String driverName, String driverPhone, String resourceName,
                                      LocalDate date, LocalTime start, LocalTime end, String status, BigDecimal price) {}
}
