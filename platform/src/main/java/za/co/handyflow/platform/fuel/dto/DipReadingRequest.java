package za.co.handyflow.platform.fuel.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.Instant;
public record DipReadingRequest(
        @NotNull @jakarta.validation.constraints.PositiveOrZero(message = "Dip reading cannot be negative") BigDecimal actualLitres,
        @NotNull Instant readAt,
        String readBy, String notes
) {}