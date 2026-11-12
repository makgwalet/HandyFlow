package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** C04 part 2: routes (one tanker and driver, several stops in order). */
public final class RouteDtos {
    private RouteDtos() {}

    public record CreateRouteRequest(@NotNull LocalDate routeDate, @Size(max = 100) String label, @NotNull UUID tankerId, @NotNull UUID driverEmployeeId) {}

    public record AddStopRequest(@NotNull UUID deliveryId, @NotEmpty List<AllocationRequest.CompartmentLoad> loads) {}

    public record StopOrderRequest(@NotEmpty List<UUID> deliveryIds) {}

    public record CancelRouteRequest(@NotBlank @Size(max = 300) String reason) {}

    public record StopView(UUID deliveryId, int sequence, UUID customerId, String customerName, String fuelType, BigDecimal litres, String status,
                           Instant scheduledAt, Instant windowStart, Instant windowEnd, int failedAttempts) {}

    /** state: PLANNED (nothing delivered), IN_PROGRESS, COMPLETED (every stop delivered or cancelled) or CANCELLED. */
    public record RouteView(UUID id, LocalDate routeDate, String label, String status, String state, UUID tankerId, String tankerRegistration,
                            UUID driverEmployeeId, String driverName, BigDecimal capacityLitres, BigDecimal plannedLitres,
                            int stopCount, int deliveredCount, String cancelReason, List<StopView> stops) {}

    /** Phase 8b: one stop in a suggested order. movable is false for a stop already on the road or delivered; it keeps its place. */
    public record SuggestedStop(UUID deliveryId, int sequence, int previousSequence, String customerName, String status, boolean movable, boolean moved,
                                Instant windowStart, Instant windowEnd, boolean hasPoint) {}

    /**
     * A suggestion only; nothing is saved. method = STRAIGHT_LINE or WINDOWS_AND_DISTANCE. The km figures are straight-line, cover the movable stops only
     * (from the start point and back when one was given) and are null unless the method is STRAIGHT_LINE. sitesWithoutPoint names the customers whose site has no coordinates.
     */
    public record SuggestedOrder(UUID routeId, String method, boolean changed, BigDecimal currentKm, BigDecimal suggestedKm, BigDecimal savingKm,
                                 List<SuggestedStop> stops, List<String> sitesWithoutPoint, String note) {}
}
