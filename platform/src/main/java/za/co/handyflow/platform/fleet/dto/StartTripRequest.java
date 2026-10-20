package za.co.handyflow.platform.fleet.dto;
import jakarta.validation.constraints.NotNull;
import java.time.Instant; import java.util.UUID;
public record StartTripRequest(
        UUID guardId,
        String driverName,
        String purpose,
        String tripType,        // BUSINESS | PRIVATE — defaults to BUSINESS
        String startLocation,
        @NotNull Integer startOdometer,
        @NotNull Instant startAt,
        // NEW (chunk 6): link the trip to a Driver record. Optional — driverName alone still works,
        // but only trips with a driverId count towards that driver's score. When given, driverName
        // defaults to the driver's full name.
        UUID driverId
) {}