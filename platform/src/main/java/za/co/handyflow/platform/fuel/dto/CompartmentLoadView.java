package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** What one load puts in one compartment of a tanker, for the compartment view. Litres are as planned at allocation. */
public record CompartmentLoadView(UUID deliveryId, String status, Instant scheduledAt, String customerName, String fuelType,
                                  UUID compartmentId, BigDecimal litres) {}
