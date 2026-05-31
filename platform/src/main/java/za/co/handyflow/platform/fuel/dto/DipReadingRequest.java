package za.co.handyflow.platform.fuel.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.Instant;
public record DipReadingRequest(
        @NotNull BigDecimal actualLitres,
        @NotNull Instant readAt,
        String readBy, String notes
) {}