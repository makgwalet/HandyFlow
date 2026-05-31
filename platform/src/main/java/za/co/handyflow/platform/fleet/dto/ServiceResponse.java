package za.co.handyflow.platform.fleet.dto;
import java.math.BigDecimal; import java.time.Instant;
import java.time.LocalDate; import java.util.UUID;
public record ServiceResponse(
        UUID id, UUID vehicleId, String type, String description,
        LocalDate serviceDate, Integer odometerAtService,
        BigDecimal cost, String supplier, Instant createdAt
) {}