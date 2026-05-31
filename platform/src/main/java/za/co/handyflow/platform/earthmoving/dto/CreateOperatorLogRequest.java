package za.co.handyflow.platform.earthmoving.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record CreateOperatorLogRequest(
        UUID guardId, String operatorName, String siteName,
        @NotNull Instant startedAt, BigDecimal startHours
) {}