package za.co.handyflow.platform.fleet.dto;
import jakarta.validation.constraints.NotNull;
import java.time.Instant; import java.util.UUID;
public record StartTripRequest(
        UUID guardId, String driverName, String purpose,
        String startLocation, @NotNull Integer startOdometer,
        @NotNull Instant startAt
) {}