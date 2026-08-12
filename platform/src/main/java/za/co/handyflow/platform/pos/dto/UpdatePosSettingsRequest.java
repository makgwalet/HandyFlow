package za.co.handyflow.platform.pos.dto;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
public record UpdatePosSettingsRequest(
        @DecimalMin("0.00") BigDecimal cashVarianceToleranceAmount,
        @DecimalMin("0.00") BigDecimal cashVarianceTolerancePct,
        @DecimalMin("0.00") BigDecimal cashVarianceCriticalAmount,
        @DecimalMin("0.00") BigDecimal cashVarianceCriticalPct
) {}