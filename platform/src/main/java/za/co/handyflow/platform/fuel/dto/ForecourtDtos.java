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
                              @NotNull @Positive @DecimalMax("1000") BigDecimal pricePerLitre, Boolean active,
                              @Size(max = 40) String dispenserLabel, @Min(1) @Max(20) Integer nozzleNumber) {
        public PumpRequest(String name, UUID tankId, BigDecimal pricePerLitre, Boolean active) { this(name, tankId, pricePerLitre, active, null, null); }
    }

    public record PumpResponse(UUID id, String name, UUID tankId, String tankName, String fuelType, BigDecimal pricePerLitre,
                               BigDecimal lastMeter, boolean active, boolean onShift,
                               String dispenserLabel, Integer nozzleNumber) {
        public PumpResponse(UUID id, String name, UUID tankId, String tankName, String fuelType, BigDecimal pricePerLitre,
                            BigDecimal lastMeter, boolean active, boolean onShift) {
            this(id, name, tankId, tankName, fuelType, pricePerLitre, lastMeter, active, onShift, null, null);
        }
    }

    public record OpenReading(@NotNull UUID pumpId, @NotNull @PositiveOrZero BigDecimal openingMeter) {}

    public record OpenShiftRequest(@NotBlank @Size(max = 120) String attendantName, @Size(max = 500) String notes,
                                   @NotEmpty @Valid List<OpenReading> readings) {}

    public record CloseReading(@NotNull UUID pumpId, @NotNull @PositiveOrZero BigDecimal closingMeter, @PositiveOrZero BigDecimal testLitres) {}

    public record CloseShiftRequest(@NotNull @PositiveOrZero BigDecimal cash, @NotNull @PositiveOrZero BigDecimal card,
                                    @NotNull @PositiveOrZero BigDecimal account, @NotNull @PositiveOrZero BigDecimal other,
                                    @Size(max = 500) String varianceReason, @NotEmpty @Valid List<CloseReading> readings,
                                    @PositiveOrZero BigDecimal fleetCard) {
        /** Without a fleet-card amount (older clients): treated as 0. */
        public CloseShiftRequest(BigDecimal cash, BigDecimal card, BigDecimal account, BigDecimal other, String varianceReason, List<CloseReading> readings) {
            this(cash, card, account, other, varianceReason, readings, null);
        }
    }

    public record ReverseShiftRequest(@NotBlank @Size(min = 5, max = 500) String reason) {}

    public record ReadingResponse(UUID pumpId, String pumpName, String fuelType, BigDecimal openingMeter, BigDecimal openingGap,
                                  BigDecimal closingMeter, BigDecimal testLitres, BigDecimal litresSold, BigDecimal pricePerLitre, BigDecimal salesValue) {}

    public record ShiftResponse(UUID id, String shiftNumber, String attendantName, String status, Instant openedAt, Instant closedAt,
                                BigDecimal cash, BigDecimal card, BigDecimal account, BigDecimal other, BigDecimal expected,
                                BigDecimal litresSold, BigDecimal variance, String varianceReason, String notes, String reverseReason,
                                List<ReadingResponse> readings, BigDecimal fleetCard, BigDecimal adjustments, BigDecimal tolerance,
                                String reviewStatus, String reviewNote, Instant reviewedAt, Instant providerOpenedAt, Instant providerClosedAt) {

        /** Before provider times existed. */
        public ShiftResponse(UUID id, String shiftNumber, String attendantName, String status, Instant openedAt, Instant closedAt,
                             BigDecimal cash, BigDecimal card, BigDecimal account, BigDecimal other, BigDecimal expected,
                             BigDecimal litresSold, BigDecimal variance, String varianceReason, String notes, String reverseReason,
                             List<ReadingResponse> readings, BigDecimal fleetCard, BigDecimal adjustments, BigDecimal tolerance,
                             String reviewStatus, String reviewNote, Instant reviewedAt) {
            this(id, shiftNumber, attendantName, status, openedAt, closedAt, cash, card, account, other, expected, litresSold, variance, varianceReason, notes,
                    reverseReason, readings, fleetCard, adjustments, tolerance, reviewStatus, reviewNote, reviewedAt, null, null);
        }
    }

    public record AdjustmentRequest(@NotBlank @Pattern(regexp = "REFUND|VOID|DISCOUNT", message = "Kind must be REFUND, VOID or DISCOUNT") String kind,
                                    @NotNull @Positive @DecimalMax("1000000") BigDecimal amount,
                                    @NotBlank @Size(min = 5, max = 300, message = "Reason must be 5 to 300 characters") String reason) {}

    public record DecisionNote(@Size(max = 300) String note) {}

    public record AdjustmentView(UUID id, UUID shiftId, String kind, BigDecimal amount, String reason, String status, UUID recordedBy,
                                 Instant recordedAt, UUID decidedBy, Instant decidedAt, String decisionNote) {}

    public record FuelTotal(String fuelType, BigDecimal litres, BigDecimal sales) {}

    public record ForecourtSummary(int days, int shifts, BigDecimal litres, BigDecimal sales, BigDecimal variance, int flaggedShifts, List<FuelTotal> byFuel) {}
}
