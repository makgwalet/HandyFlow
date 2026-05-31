package za.co.handyflow.platform.security.dto;
import jakarta.validation.constraints.NotNull;
import java.time.Instant; import java.util.UUID;
public record CreateShiftRequest(
        @NotNull UUID siteId, @NotNull UUID guardId,
        @NotNull Instant startAt, @NotNull Instant endAt, String notes
) {}
