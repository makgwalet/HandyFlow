package za.co.handyflow.platform.fleet.dto;
import java.math.BigDecimal; import java.time.Instant;
import java.time.LocalDate; import java.util.UUID;
public record FuelFillupResponse(
        UUID id,
        UUID vehicleId,
        LocalDate filledAt,
        BigDecimal litres,
        BigDecimal pricePerLitre,
        BigDecimal totalCost,
        Integer odometerAtFillup,
        String station,
        String receiptRef,
        boolean fullTank,
        Instant createdAt
) {}
