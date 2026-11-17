package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** D1: tolerances by depot and fuel type, the repeated-variance rule, and the log of automatic acceptances. */
public final class ShiftControlDtos {
    private ShiftControlDtos() {}

    /** scope DEPOT: key is the depot id, amount is a rand cash variance. scope PRODUCT: key is the fuel type, amount is litres for gauge readings. */
    public record OverrideView(String scope, String key, String label, BigDecimal amount) {}

    public record OverrideRequest(@NotBlank @Size(max = 10) String scope, @NotBlank @Size(max = 60) String key, @NotNull @DecimalMin("0") BigDecimal amount) {}

    /** Null count means the repeated-variance alert is off. */
    public record Policy(Integer repeatCount, Integer repeatDays) {}

    public record PolicyRequest(@Min(2) @Max(50) Integer repeatCount, @Min(1) @Max(365) Integer repeatDays) {}

    public record Controls(List<OverrideView> overrides, Policy policy) {}

    public record AutoAcceptLogView(UUID id, UUID shiftId, String shiftNumber, String attendantName, BigDecimal varianceAmount, BigDecimal toleranceApplied,
                                    String actor, Instant acceptedAt) {}
}
