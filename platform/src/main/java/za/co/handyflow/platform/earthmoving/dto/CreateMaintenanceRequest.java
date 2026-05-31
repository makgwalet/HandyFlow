package za.co.handyflow.platform.earthmoving.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.Instant;
public record CreateMaintenanceRequest(
        @NotBlank String type, @NotBlank String description,
        @NotNull Instant performedAt, BigDecimal hoursAtService,
        BigDecimal cost, String supplier, String invoiceRef
) {}