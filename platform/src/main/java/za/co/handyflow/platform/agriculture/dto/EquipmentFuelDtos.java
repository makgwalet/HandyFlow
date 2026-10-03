package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.AllocationShare;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for costing equipment use and allocating fuel (ADR-001, W4). */
public final class EquipmentFuelDtos {

    private EquipmentFuelDtos() {}

    /**
     * A machine that can be costed. {@code operatingRatePerHour} is Fleet's rate for it (service and repairs only, not fuel, not depreciation)
     * and is null until someone sets it in Fleet, in which case its use cannot be costed yet.
     */
    public record EquipmentOption(UUID vehicleId, String registration, String description, BigDecimal engineHours, BigDecimal operatingRatePerHour) {}

    /** One day's use of a machine, split across the targets it worked on (percentages total 100). */
    public record CostEquipmentRequest(@NotNull UUID vehicleId, @NotNull LocalDate workDate, @NotNull @Positive BigDecimal hours, String notes,
                                       @NotNull @Valid List<AllocationShare> allocations) {}

    /** One own-fuel dispatch not yet allocated. {@code costPerLitre} and {@code cost} are null when Fuel recorded no cost for it. */
    public record FuelDispatchRow(UUID dispatchId, LocalDate date, String tankName, UUID vehicleId, String vehicle, String recipientName,
                                  BigDecimal litres, BigDecimal costPerLitre, BigDecimal cost, BigDecimal hoursReading) {}

    /** {@code alreadyAllocated} counts the dispatches in the range that are done and so are not listed. */
    public record FuelOverview(LocalDate from, LocalDate to, List<FuelDispatchRow> dispatches, int alreadyAllocated) {}

    /** Allocates ONE dispatch's whole cost across targets on the farm (percentages total 100). */
    public record AllocateFuelRequest(@NotNull UUID dispatchId, String notes, @NotNull @Valid List<AllocationShare> allocations) {}
}
