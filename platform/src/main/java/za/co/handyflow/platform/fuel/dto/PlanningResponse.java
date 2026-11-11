package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One day's loads and what each tanker is carrying. Unallocated loads are the ones needing attention. */
public record PlanningResponse(LocalDate date, List<PlanLoad> loads, List<TankerDay> tankers) {
    public record PlanLoad(UUID deliveryId, String status, Instant scheduledAt, UUID customerId, String customerName,
                           String fuelType, BigDecimal litres, UUID tankerId, String tankerRegistration,
                           UUID driverEmployeeId, String driverName, boolean allocated) {}
    public record TankerDay(UUID tankerId, String registration, BigDecimal capacity, int loads, BigDecimal litres) {}
}
