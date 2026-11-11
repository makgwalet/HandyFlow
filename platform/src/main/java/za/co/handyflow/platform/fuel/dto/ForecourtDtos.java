package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Retail forecourt: pumps, attendant shifts and the summary. */
public final class ForecourtDtos {
    private ForecourtDtos() {}

    public record PumpRequest(@NotBlank @Size(max = 40) String name, @NotNull UUID tankId,
                              @NotNull @Positive @DecimalMax("1000") BigDecimal pricePerLitre, Boolean active) {}

    public record PumpResponse(UUID id, String name, UUID tankId, String tankName, String fuelType, BigDecimal pricePerLitre,
                               BigDecimal lastMeter, boolean active, boolean onShift) {}

    public record OpenReading(@NotNull UUID pumpId, @NotNull @PositiveOrZero BigDecimal openingMeter) {}

    public record OpenShiftRequest(@NotBlank @Size(max = 120) String attendantName, @Size(max = 500) String notes,
                                   @NotEmpty @Valid List<OpenReading> readings) {}

    public record CloseReading(@NotNull UUID pumpId, @NotNull @PositiveOrZero BigDecimal closingMeter, @PositiveOrZero BigDecimal testLitres) {}

    public record CloseShiftRequest(@NotNull @PositiveOrZero BigDecimal cash, @NotNull @PositiveOrZero BigDecimal card,
                                    @NotNull @PositiveOrZero BigDecimal account, @NotNull @PositiveOrZero BigDecimal other,
                                    @Size(max = 500) String varianceReason, @NotEmpty @Valid List<CloseReading> readings) {}

    public record ReverseShiftRequest(@NotBlank @Size(min = 5, max = 500) String reason) {}

    public record ReadingResponse(UUID pumpId, String pumpName, String fuelType, BigDecimal openingMeter, BigDecimal openingGap,
                                  BigDecimal closingMeter, BigDecimal testLitres, BigDecimal litresSold, BigDecimal pricePerLitre, BigDecimal salesValue) {}

    public record ShiftResponse(UUID id, String shiftNumber, String attendantName, String status, Instant openedAt, Instant closedAt,
                                BigDecimal cash, BigDecimal card, BigDecimal account, BigDecimal other, BigDecimal expected,
                                BigDecimal litresSold, BigDecimal variance, String varianceReason, String notes, String reverseReason,
                                List<ReadingResponse> readings) {}

    public record FuelTotal(String fuelType, BigDecimal litres, BigDecimal sales) {}

    public record ForecourtSummary(int days, int shifts, BigDecimal litres, BigDecimal sales, BigDecimal variance, int flaggedShifts, List<FuelTotal> byFuel) {}
}
