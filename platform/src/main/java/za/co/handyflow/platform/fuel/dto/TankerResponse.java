package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TankerResponse(UUID id, String registration, String name, boolean active, BigDecimal totalCapacity,
                             List<CompartmentResponse> compartments, long openLoads) {
    public record CompartmentResponse(UUID id, int number, BigDecimal capacityLitres) {}
}
