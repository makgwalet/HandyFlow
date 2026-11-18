package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One load from the office's side: what happened to it, oldest first. The time a driver pressed Start is not recorded on the load,
 * so "on the road" has no event of its own; the status says whether it is.
 */
public final class DeliveryExecutionDtos {
    private DeliveryExecutionDtos() {}

    /** @param type SCHEDULED, LOADED, ARRIVAL_CHECK, EVIDENCE, FAILED_ATTEMPT, DELIVERED, RECEIPT or REVERSED
     *  @param severity OK, REVIEW or PROBLEM, for colouring only */
    public record Event(Instant at, String type, String text, String severity) {}

    public record Execution(UUID deliveryId, String status, String driverName, String vehicleReg, String receiverName, boolean signedOnBehalf,
                            BigDecimal litresOrdered, BigDecimal litresLoaded, BigDecimal litresDelivered,
                            BigDecimal meterStart, BigDecimal meterEnd, int failedAttempts, List<Event> events) {}
}
