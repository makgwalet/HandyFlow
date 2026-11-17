package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public final class AutoAcceptDtos {
    private AutoAcceptDtos() {}

    public record Setting(boolean shiftsEnabled, boolean gaugesEnabled, BigDecimal gaugeMaxDifferenceLitres) {}

    public record SaveRequest(boolean shiftsEnabled, boolean gaugesEnabled, @PositiveOrZero BigDecimal gaugeMaxDifferenceLitres) {}
}
