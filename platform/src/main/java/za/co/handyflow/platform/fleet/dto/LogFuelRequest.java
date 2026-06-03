package za.co.handyflow.platform.fleet.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.LocalDate;
public record LogFuelRequest(
        @NotNull LocalDate filledAt,
        @NotNull BigDecimal litres,
        BigDecimal pricePerLitre,      // used to compute totalCost
        BigDecimal totalCost,          // alternatively provide totalCost directly
        Integer odometerAtFillup,
        String station,                // e.g. "Engen Sandton"
        String receiptRef,
        boolean fullTank               // was it a full tank? used for consumption calcs
) {}