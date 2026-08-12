package za.co.handyflow.platform.pos.dto;
import java.math.BigDecimal;
public record PosSettingsResponse(
        BigDecimal cashVarianceToleranceAmount,
        BigDecimal cashVarianceTolerancePct,
        BigDecimal cashVarianceCriticalAmount,
        BigDecimal cashVarianceCriticalPct
) {}