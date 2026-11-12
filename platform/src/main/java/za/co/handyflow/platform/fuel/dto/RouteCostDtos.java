package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** F02: actual versus estimated cost of routes. */
public final class RouteCostDtos {
    private RouteCostDtos() {}

    public record CostEntryRequest(@NotBlank String category, @NotNull BigDecimal amount, BigDecimal quantity, @Size(max = 10) String unit, @Size(max = 300) String note) {}
    public record VoidEntryRequest(@NotBlank @Size(max = 300) String reason) {}

    public record CostEntryView(UUID id, UUID routeId, String category, BigDecimal amount, BigDecimal quantity, String unit, String note, String status,
                                String voidReason, UUID recordedBy, Instant recordedAt) {}

    /**
     * One route. Estimates come from the tenant's own cost rates (null when the rates or the distances needed are missing).
     * variance = comparable actual (vehicle + driver) minus estimate, only when both exist. Tolls and other costs are shown but not compared.
     */
    public record RouteCostRow(UUID routeId, LocalDate routeDate, String label, String tankerRegistration, String driverName, String state,
                               int stops, int deliveredCount, BigDecimal litresDelivered,
                               BigDecimal estimateVehicle, BigDecimal estimateDriver, BigDecimal estimateTotal,
                               BigDecimal actualVehicle, BigDecimal actualDriver, BigDecimal actualTolls, BigDecimal actualOther, BigDecimal actualTotal,
                               BigDecimal variance, BigDecimal variancePct, BigDecimal actualCostPerLitre, boolean reconciled, List<String> flags) {}

    public record Rollup(String key, String label, int routes, int reconciledRoutes, BigDecimal litres, BigDecimal estimate, BigDecimal comparableActual,
                         BigDecimal variance, BigDecimal actualTotal, BigDecimal actualCostPerLitre) {}

    public record RouteCostReport(LocalDate from, LocalDate to, boolean ratesSet, int unreconciledRoutes, List<RouteCostRow> routes, List<Rollup> byTanker, List<Rollup> byDepot) {}
}
