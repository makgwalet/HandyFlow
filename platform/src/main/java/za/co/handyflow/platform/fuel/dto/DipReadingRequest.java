package za.co.handyflow.platform.fuel.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.Instant;
public record DipReadingRequest(
        @NotNull @jakarta.validation.constraints.PositiveOrZero(message = "Dip reading cannot be negative") BigDecimal actualLitres,
        @NotNull Instant readAt,
        String readBy, String notes,
        // Made once on the phone and kept with a queued reading, so a retry after a lost reply returns the first record instead of adding a second.
        java.util.UUID clientRequestId
) {
    public DipReadingRequest(BigDecimal actualLitres, Instant readAt, String readBy, String notes) { this(actualLitres, readAt, readBy, notes, null); }
}